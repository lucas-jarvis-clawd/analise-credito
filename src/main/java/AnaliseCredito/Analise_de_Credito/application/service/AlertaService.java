package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.DadosBI;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AlertaService - Calcula alertas de badges para pedidos no Kanban.
 *
 * Regras de alertas:
 * 1. SIMEI > LIMITE - Cliente SIMEI com pedido acima do limite configurado
 * 2. GRUPO > X SIMEIS - Grupo com mais de X clientes SIMEI com pedidos
 * 3. PEDIDO > LIMITE - Pedido com valor acima do limite aprovado do grupo
 * 4. TOTAL > LIMITE - Soma de pedidos abertos do grupo acima do limite
 * 5. RESTRIÇÕES (X) - Contagem de restrições cadastradas (Pefin + Protestos + Ações + Cheques)
 * 6. SCORE BAIXO - Score Boa Vista abaixo do threshold configurado
 *
 * Usado pelo KanbanController para exibir badges visuais nos cards dos pedidos.
 */
@Service
public class AlertaService {

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    /**
     * Calcula todos os alertas aplicáveis para um pedido.
     *
     * @param pedido Pedido para calcular alertas
     * @return Lista de strings de alerta (ex: "SIMEI > LIMITE", "PEDIDO > LIMITE")
     * @throws RuntimeException se configuração não for encontrada
     */
    public List<String> calcularAlertas(Pedido pedido) {
        List<String> alerts = new ArrayList<>();

        Cliente cliente = pedido.getCliente();
        GrupoEconomico grupo = cliente.getGrupoEconomico();
        Configuracao config = getConfiguracao();

        // 1. SIMEI > LIMITE
        if (Boolean.TRUE.equals(cliente.getSimei()) &&
                pedido.getValor().compareTo(config.getLimiteSimei()) > 0) {
            alerts.add("SIMEI > LIMITE");
        }

        // 2. GRUPO > X SIMEIS
        long simeiComPedidos = grupo.getClientes().stream()
                .filter(c -> Boolean.TRUE.equals(c.getSimei()))
                .filter(c -> !c.getPedidos().isEmpty())
                .count();

        if (simeiComPedidos > config.getMaxSimeisPorGrupo()) {
            alerts.add("GRUPO > " + config.getMaxSimeisPorGrupo() + " SIMEIS");
        }

        // 3. PEDIDO > LIMITE
        if (pedido.getValor().compareTo(grupo.getLimiteAprovado()) > 0) {
            alerts.add("PEDIDO > LIMITE");
        }

        // 4. TOTAL > LIMITE
        BigDecimal totalPedidosAbertos = calcularTotalPedidosAbertos(grupo);
        if (totalPedidosAbertos.compareTo(grupo.getLimiteAprovado()) > 0) {
            alerts.add("TOTAL > LIMITE");
        }

        // 5. RESTRIÇÕES (X)
        int restricoesTotal = cliente.getPefins().size() +
                cliente.getProtestos().size() +
                cliente.getAcoesJudiciais().size() +
                cliente.getCheques().size();

        if (restricoesTotal > 0) {
            alerts.add("RESTRIÇÕES (" + restricoesTotal + ")");
        }

        // 6. SCORE BAIXO
        if (cliente.getScoreBoaVista() != null &&
                cliente.getScoreBoaVista() < config.getScoreBaixoThreshold()) {
            alerts.add("SCORE BAIXO");
        }

        // 7. ALERTA: Pedido acima da sazonalidade
        if (config.getConsiderarSazonalidade() && pedido.getNomeColecao() != null) {
            BigDecimal mediaColecao = calcularMediaHistoricaColecao(pedido.getNomeColecao(), cliente);
            if (mediaColecao.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal limiteSazonal = mediaColecao.multiply(new BigDecimal("1.50")); // 50% acima
                if (pedido.getValor().compareTo(limiteSazonal) > 0) {
                    alerts.add("PEDIDO ACIMA DA SAZONALIDADE (+50%)");
                }
            }
        }

        // 8. ALERTA: Deterioração de score BI
        DadosBI dadosRecentes = buscarDadosBIMaisRecente(grupo);
        if (dadosRecentes != null) {
            Integer variacaoScore = dadosRecentes.getVariacaoScore();
            if (variacaoScore != null && variacaoScore < -100) {
                alerts.add("DETERIORAÇÃO DE SCORE (" + variacaoScore + " pontos)");
            }
        }

        // 9. ALERTA: Aumento de atraso médio
        if (dadosRecentes != null) {
            BigDecimal variacaoAtraso = dadosRecentes.getVariacaoAtraso();
            if (variacaoAtraso != null && variacaoAtraso.compareTo(new BigDecimal("5")) > 0) {
                alerts.add("ATRASO CRESCENTE (+" + variacaoAtraso.setScale(1, RoundingMode.HALF_UP) + " dias)");
            }
        }

        return alerts;
    }

    /**
     * Busca a configuração singleton do sistema.
     *
     * @return Configuração do sistema (ID=1)
     * @throws RuntimeException se configuração não for encontrada
     */
    private Configuracao getConfiguracao() {
        return configuracaoRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Configuração não encontrada"));
    }

    /**
     * Calcula o total de pedidos em aberto de todos os clientes do grupo.
     * Implementação real da lógica que está marcada como TODO no GrupoEconomico.
     *
     * @param grupo Grupo econômico
     * @return Soma dos valores de todos os pedidos dos clientes do grupo
     */
    private BigDecimal calcularTotalPedidosAbertos(GrupoEconomico grupo) {
        return grupo.getClientes().stream()
                .flatMap(cliente -> cliente.getPedidos().stream())
                .map(Pedido::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula média histórica de pedidos da mesma coleção.
     * Busca pedidos anteriores da mesma coleção (ex: "Verão 2025") e calcula média.
     *
     * @param nomeColecao Nome da coleção (ex: "Verão 2026")
     * @param cliente Cliente para buscar histórico
     * @return Média dos valores de pedidos da mesma coleção, ou ZERO se sem histórico
     */
    private BigDecimal calcularMediaHistoricaColecao(String nomeColecao, Cliente cliente) {
        // Busca pedidos com mesmo nome de coleção
        List<Pedido> pedidosColecao = cliente.getPedidos().stream()
                .filter(p -> nomeColecao.equals(p.getNomeColecao()))
                .filter(p -> p.getData() != null)
                .filter(p -> p.getData().isBefore(LocalDate.now().minusMonths(6))) // Pelo menos 6 meses atrás
                .toList();

        if (pedidosColecao.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal soma = pedidosColecao.stream()
                .map(Pedido::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return soma.divide(BigDecimal.valueOf(pedidosColecao.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Busca o DadosBI mais recente do grupo econômico.
     *
     * @param grupo Grupo econômico
     * @return DadosBI mais recente, ou null se não houver dados
     */
    private DadosBI buscarDadosBIMaisRecente(GrupoEconomico grupo) {
        if (grupo.getDadosBI() == null || grupo.getDadosBI().isEmpty()) {
            return null;
        }

        return grupo.getDadosBI().stream()
                .max((d1, d2) -> {
                    if (d1.getColecao() == null) return -1;
                    if (d2.getColecao() == null) return 1;
                    return d1.getColecao().compareTo(d2.getColecao());
                })
                .orElse(null);
    }
}
