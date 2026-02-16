package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AnaliseRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * WorkflowService - Gerencia transições de estado do workflow de análise.
 *
 * Responsabilidades:
 * 1. Validar transições permitidas para cada tipo de workflow (BASE_PRAZO vs CLIENTE_NOVO)
 * 2. Verificar regras de alçada (requerAprovacaoGestor)
 * 3. Atualizar status da análise
 * 4. Registrar metadata (data, analista)
 * 5. Aplicar efeitos colaterais (atualizar limite do grupo ao finalizar)
 *
 * WORKFLOWS:
 *
 * BASE_PRAZO:
 * PENDENTE → EM_ANALISE_FINANCEIRO → PARECER_APROVADO/REPROVADO →
 * [AGUARDANDO_APROVACAO_GESTOR] → [REANALISE_COMERCIAL_SOLICITADA] →
 * [REANALISADO_APROVADO/REPROVADO] → FINALIZADO
 *
 * CLIENTE_NOVO:
 * PENDENTE → DOCUMENTACAO_SOLICITADA → DOCUMENTACAO_ENVIADA →
 * PARECER_APROVADO/REPROVADO → [AGUARDANDO_APROVACAO_GESTOR] →
 * [REANALISE_COMERCIAL_SOLICITADA] → [REANALISADO_APROVADO/REPROVADO] → FINALIZADO
 */
@Service
public class WorkflowService {

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    // Mapa de transições válidas por workflow
    private static final Map<TipoWorkflow, Map<StatusWorkflow, Set<StatusWorkflow>>> TRANSICOES_VALIDAS;

    static {
        TRANSICOES_VALIDAS = new EnumMap<>(TipoWorkflow.class);

        // BASE_PRAZO workflow transitions
        Map<StatusWorkflow, Set<StatusWorkflow>> basePrazoTransitions = new EnumMap<>(StatusWorkflow.class);
        basePrazoTransitions.put(StatusWorkflow.PENDENTE,
            EnumSet.of(StatusWorkflow.EM_ANALISE_FINANCEIRO));
        basePrazoTransitions.put(StatusWorkflow.EM_ANALISE_FINANCEIRO,
            EnumSet.of(StatusWorkflow.PARECER_APROVADO, StatusWorkflow.PARECER_REPROVADO));
        basePrazoTransitions.put(StatusWorkflow.PARECER_APROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        basePrazoTransitions.put(StatusWorkflow.PARECER_REPROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        basePrazoTransitions.put(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR,
            EnumSet.of(StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        basePrazoTransitions.put(StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA,
            EnumSet.of(StatusWorkflow.REANALISADO_APROVADO, StatusWorkflow.REANALISADO_REPROVADO));
        basePrazoTransitions.put(StatusWorkflow.REANALISADO_APROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.FINALIZADO));
        basePrazoTransitions.put(StatusWorkflow.REANALISADO_REPROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.FINALIZADO));
        basePrazoTransitions.put(StatusWorkflow.FINALIZADO,
            EnumSet.noneOf(StatusWorkflow.class)); // Terminal state

        // CLIENTE_NOVO workflow transitions (pipeline com gates)
        Map<StatusWorkflow, Set<StatusWorkflow>> clienteNovoTransitions = new EnumMap<>(StatusWorkflow.class);
        clienteNovoTransitions.put(StatusWorkflow.PENDENTE,
            EnumSet.of(StatusWorkflow.FAZER_CONSULTAS, StatusWorkflow.CONSULTA_PROTESTOS,
                       StatusWorkflow.SOLICITAR_CANCELAMENTO, StatusWorkflow.ENCAMINHADO_ANTECIPADO));
        clienteNovoTransitions.put(StatusWorkflow.FAZER_CONSULTAS,
            EnumSet.of(StatusWorkflow.CONSULTA_PROTESTOS, StatusWorkflow.SOLICITAR_CANCELAMENTO,
                       StatusWorkflow.ENCAMINHADO_ANTECIPADO));
        clienteNovoTransitions.put(StatusWorkflow.CONSULTA_PROTESTOS,
            EnumSet.of(StatusWorkflow.VERIFICACAO_LOJA_FISICA, StatusWorkflow.ENCAMINHADO_ANTECIPADO));
        clienteNovoTransitions.put(StatusWorkflow.VERIFICACAO_LOJA_FISICA,
            EnumSet.of(StatusWorkflow.CONSULTA_SCORE_RESTRICOES, StatusWorkflow.ENCAMINHADO_ANTECIPADO));
        clienteNovoTransitions.put(StatusWorkflow.CONSULTA_SCORE_RESTRICOES,
            EnumSet.of(StatusWorkflow.EM_ANALISE_CLIENTE_NOVO, StatusWorkflow.ENCAMINHADO_ANTECIPADO));
        clienteNovoTransitions.put(StatusWorkflow.EM_ANALISE_CLIENTE_NOVO,
            EnumSet.of(StatusWorkflow.PARECER_APROVADO, StatusWorkflow.PARECER_REPROVADO));
        clienteNovoTransitions.put(StatusWorkflow.PARECER_APROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        clienteNovoTransitions.put(StatusWorkflow.PARECER_REPROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        clienteNovoTransitions.put(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR,
            EnumSet.of(StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA, StatusWorkflow.FINALIZADO));
        clienteNovoTransitions.put(StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA,
            EnumSet.of(StatusWorkflow.REANALISADO_APROVADO, StatusWorkflow.REANALISADO_REPROVADO));
        clienteNovoTransitions.put(StatusWorkflow.REANALISADO_APROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.FINALIZADO));
        clienteNovoTransitions.put(StatusWorkflow.REANALISADO_REPROVADO,
            EnumSet.of(StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR, StatusWorkflow.FINALIZADO));
        // Terminal states
        clienteNovoTransitions.put(StatusWorkflow.SOLICITAR_CANCELAMENTO,
            EnumSet.noneOf(StatusWorkflow.class));
        clienteNovoTransitions.put(StatusWorkflow.ENCAMINHADO_ANTECIPADO,
            EnumSet.noneOf(StatusWorkflow.class));
        clienteNovoTransitions.put(StatusWorkflow.FINALIZADO,
            EnumSet.noneOf(StatusWorkflow.class));

        TRANSICOES_VALIDAS.put(TipoWorkflow.BASE_PRAZO, basePrazoTransitions);
        TRANSICOES_VALIDAS.put(TipoWorkflow.CLIENTE_NOVO, clienteNovoTransitions);
    }

    /**
     * Transiciona a análise para um novo status, aplicando todas as regras de negócio.
     *
     * @param analise Análise a ser transicionada
     * @param novoStatus Novo status desejado
     * @param analistaResponsavel Nome do analista responsável pela transição
     * @throws IllegalStateException se a transição for inválida
     * @throws RuntimeException se configuração não for encontrada
     */
    @Transactional
    public void transicionar(Analise analise, StatusWorkflow novoStatus, String analistaResponsavel) {
        // 1. Validar transição
        TipoWorkflow workflow = analise.getPedido().getWorkflow();
        if (!isTransicaoValida(analise.getStatusWorkflow(), novoStatus, workflow)) {
            throw new IllegalStateException(
                String.format("Transição inválida de %s para %s no workflow %s",
                    analise.getStatusWorkflow(), novoStatus, workflow)
            );
        }

        // 2. Atualizar status e analista
        analise.setStatusWorkflow(novoStatus);
        analise.setAnalistaResponsavel(analistaResponsavel);

        // 3. Aplicar lógica específica por estado
        aplicarLogicaEspecifica(analise, novoStatus);

        // 4. Persistir alterações
        analiseRepository.save(analise);
    }

    /**
     * Aplica lógica de negócio específica para cada estado.
     */
    private void aplicarLogicaEspecifica(Analise analise, StatusWorkflow novoStatus) {
        switch (novoStatus) {
            case EM_ANALISE_FINANCEIRO:
                if (analise.getDataInicio() == null) {
                    analise.setDataInicio(LocalDateTime.now());
                }
                break;

            case DOCUMENTACAO_ENVIADA:
                if (analise.getDataInicio() == null) {
                    analise.setDataInicio(LocalDateTime.now());
                }
                break;

            case EM_ANALISE_CLIENTE_NOVO:
                if (analise.getDataInicio() == null) {
                    analise.setDataInicio(LocalDateTime.now());
                }
                break;

            case PARECER_APROVADO:
            case PARECER_REPROVADO:
                if (requerAprovacaoGestor(analise)) {
                    analise.setRequerAprovacaoGestor(true);
                }
                break;

            case REANALISADO_APROVADO:
            case REANALISADO_REPROVADO:
                if (requerAprovacaoGestor(analise)) {
                    analise.setRequerAprovacaoGestor(true);
                }
                break;

            case SOLICITAR_CANCELAMENTO:
                analise.setDataFim(LocalDateTime.now());
                break;

            case ENCAMINHADO_ANTECIPADO:
                analise.setDataFim(LocalDateTime.now());
                // Atualizar tipo do cliente para ANTECIPADO
                Cliente clienteAntecipado = clienteRepository.findById(analise.getClienteId())
                        .orElse(null);
                if (clienteAntecipado != null) {
                    clienteAntecipado.setTipoCliente(
                            AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente.ANTECIPADO);
                    clienteRepository.save(clienteAntecipado);
                }
                break;

            case FINALIZADO:
                analise.setDataFim(LocalDateTime.now());
                if (analise.getLimiteAprovado() != null &&
                    analise.getLimiteAprovado().compareTo(BigDecimal.ZERO) > 0) {
                    atualizarLimiteGrupo(analise);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Verifica se a transição de um status para outro é válida no workflow especificado.
     *
     * @param statusAtual Status atual da análise
     * @param novoStatus Novo status desejado
     * @param workflow Tipo de workflow (BASE_PRAZO ou CLIENTE_NOVO)
     * @return true se a transição é válida, false caso contrário
     */
    public boolean isTransicaoValida(StatusWorkflow statusAtual, StatusWorkflow novoStatus, TipoWorkflow workflow) {
        if (statusAtual == null || novoStatus == null || workflow == null) {
            return false;
        }

        // Não pode transicionar para o mesmo estado
        if (statusAtual == novoStatus) {
            return false;
        }

        Map<StatusWorkflow, Set<StatusWorkflow>> transicoesDoWorkflow = TRANSICOES_VALIDAS.get(workflow);
        if (transicoesDoWorkflow == null) {
            return false;
        }

        Set<StatusWorkflow> statusPermitidos = transicoesDoWorkflow.get(statusAtual);
        return statusPermitidos != null && statusPermitidos.contains(novoStatus);
    }

    /**
     * Verifica se a análise requer aprovação de gestor baseado nas regras de alçada.
     *
     * Regras:
     * - Valor do pedido > valorAprovacaoGestor OU
     * - Total de pedidos em aberto do grupo > totalGrupoAprovacaoGestor OU
     * - Número de restrições >= restricoesAprovacaoGestor
     *
     * @param analise Análise a ser verificada
     * @return true se requer aprovação de gestor
     * @throws RuntimeException se configuração não for encontrada
     */
    public boolean requerAprovacaoGestor(Analise analise) {
        Configuracao config = configuracaoRepository.findById(1L)
            .orElseThrow(() -> new RuntimeException("Configuração não encontrada"));

        Pedido pedido = analise.getPedido();

        // Buscar cliente para obter restrições
        Cliente cliente = clienteRepository.findById(analise.getClienteId())
            .orElseThrow(() -> new RuntimeException("Cliente não encontrado: " + analise.getClienteId()));

        // Buscar grupo para obter total de pedidos
        GrupoEconomico grupo = grupoEconomicoRepository.findById(analise.getGrupoEconomicoId())
            .orElseThrow(() -> new RuntimeException("Grupo econômico não encontrado: " + analise.getGrupoEconomicoId()));

        // Calcular total de pedidos em aberto do grupo
        BigDecimal totalPedidos = calcularTotalPedidosAbertos(grupo);

        // Calcular total de restrições do cliente
        int restricoes = cliente.getTotalRestricoes();

        // Verificar cada condição
        boolean porValor = pedido.getValor().compareTo(config.getValorAprovacaoGestor()) > 0;
        boolean porTotalGrupo = totalPedidos.compareTo(config.getTotalGrupoAprovacaoGestor()) > 0;
        boolean porRestricoes = restricoes >= config.getRestricoesAprovacaoGestor();

        return porValor || porTotalGrupo || porRestricoes;
    }

    /**
     * Calcula o total de pedidos em aberto do grupo econômico.
     * Considera apenas pedidos que ainda estão em análise (dataFim == null).
     */
    private BigDecimal calcularTotalPedidosAbertos(GrupoEconomico grupo) {
        return grupo.getClientes().stream()
            .flatMap(cliente -> cliente.getPedidos().stream())
            .filter(pedido -> pedido.getAnalise() != null && pedido.getAnalise().getDataFim() == null)
            .map(Pedido::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Atualiza o limite aprovado do grupo econômico quando uma análise é finalizada.
     * O limite do grupo é atualizado com o limite aprovado na análise.
     *
     * @param analise Análise finalizada com limite aprovado
     */
    private void atualizarLimiteGrupo(Analise analise) {
        GrupoEconomico grupo = grupoEconomicoRepository.findById(analise.getGrupoEconomicoId())
            .orElseThrow(() -> new RuntimeException("Grupo econômico não encontrado: " + analise.getGrupoEconomicoId()));

        // Atualizar limite aprovado do grupo
        grupo.setLimiteAprovado(analise.getLimiteAprovado());

        // Recalcular limite disponível
        BigDecimal totalPedidosAbertos = calcularTotalPedidosAbertos(grupo);
        BigDecimal limiteDisponivel = analise.getLimiteAprovado().subtract(totalPedidosAbertos);
        grupo.setLimiteDisponivel(limiteDisponivel.max(BigDecimal.ZERO));

        grupoEconomicoRepository.save(grupo);
    }

    /**
     * Retorna os status permitidos a partir do status atual para um workflow.
     *
     * @param statusAtual Status atual
     * @param workflow Tipo de workflow
     * @return Conjunto de status permitidos (vazio se nenhum)
     */
    public Set<StatusWorkflow> getStatusPermitidos(StatusWorkflow statusAtual, TipoWorkflow workflow) {
        if (statusAtual == null || workflow == null) {
            return EnumSet.noneOf(StatusWorkflow.class);
        }

        Map<StatusWorkflow, Set<StatusWorkflow>> transicoesDoWorkflow = TRANSICOES_VALIDAS.get(workflow);
        if (transicoesDoWorkflow == null) {
            return EnumSet.noneOf(StatusWorkflow.class);
        }

        Set<StatusWorkflow> statusPermitidos = transicoesDoWorkflow.get(statusAtual);
        return statusPermitidos != null ? statusPermitidos : EnumSet.noneOf(StatusWorkflow.class);
    }
}
