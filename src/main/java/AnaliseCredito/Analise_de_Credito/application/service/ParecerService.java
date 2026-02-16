package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.DadosBIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ParecerService - Gera parecer formatado para integração com CRM.
 *
 * REGRA CRÍTICA:
 * - APENAS gera parecer para workflow CLIENTE_NOVO (bloqueio 80/36)
 * - Retorna null para BASE_PRAZO
 *
 * Formato do Parecer CRM Enriquecido (8 linhas):
 * [STATUS] DD/MM/YYYY
 * CADASTRO: TIPO - FUNDAÇÃO YYYY - SCORE BV: XXX - SCORE INT: XXX - X RESTRIÇÕES
 * HISTÓRICO: ATRASO MÉDIO: X dias - X PEDIDOS - TICKET MÉDIO: R$XXX
 * EXPOSIÇÃO: LIMITE GRUPO: R$XXX - EM USO: R$XXX (XX%) - DISPONÍVEL: R$XXX
 * ANÁLISE: ALERTA1 | ALERTA2 | ... (or "SEM ALERTAS")
 * DECISÃO: APROVAR R$XXX (XX% do solicitado) [ONLY if limiteAprovado != pedido.valor]
 * FUNDAMENTO: [observações do analista]
 * ANALISTA: FINANCEIRO/COMERCIAL - APROVADOR: Gestor Crédito [if requerAprovacaoGestor]
 */
@Service
public class ParecerService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private AlertaService alertaService;

    /**
     * Gera o parecer formatado para o CRM.
     *
     * @param analise Análise completa com pedido e cliente
     * @return Parecer formatado ou null se workflow for BASE_PRAZO
     */
    public String gerarParecerCRM(Analise analise) {
        // 1. Check workflow - ONLY generate for CLIENTE_NOVO
        Pedido pedido = analise.getPedido();
        if (pedido.getWorkflow() != TipoWorkflow.CLIENTE_NOVO) {
            return null; // Don't generate for BASE_PRAZO
        }

        // 2. Load cliente with all relationships
        Cliente cliente = clienteRepository.findById(analise.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        GrupoEconomico grupo = cliente.getGrupoEconomico();

        // 3. Build enriched parecer (8 lines)
        StringBuilder sb = new StringBuilder();

        // LINE 1: [STATUS] DD/MM/YYYY
        sb.append("[").append(analise.getDecisao() != null ? analise.getDecisao() : "EM ANÁLISE").append("] ");
        LocalDate data = analise.getDataFim() != null ?
                analise.getDataFim().toLocalDate() : LocalDate.now();
        sb.append(data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        sb.append("\n");

        // LINE 2: CADASTRO: TIPO - FUNDAÇÃO YYYY - SCORE BV: XXX - SCORE INT: XXX - X RESTRIÇÕES
        sb.append("CADASTRO: ");
        sb.append(extrairTipo(cliente.getRazaoSocial())).append(" - ");
        if (cliente.getDataFundacao() != null) {
            sb.append(cliente.getDataFundacao().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        } else {
            sb.append("N/D");
        }
        sb.append(" - SCORE BV: ").append(cliente.getScoreBoaVista() != null ? cliente.getScoreBoaVista() : "N/D");
        sb.append(" - SCORE INT: ").append(obterScoreInterno(grupo.getId()));
        sb.append(" - ").append(cliente.getTotalRestricoes()).append(" RESTRIÇÕES");
        sb.append("\n");

        // LINE 3: HISTÓRICO: ATRASO MÉDIO: X dias - X PEDIDOS - TICKET MÉDIO: R$XXX
        sb.append("HISTÓRICO: ");
        BigDecimal atrasoMedio = calcularAtrasoMedio(cliente);
        sb.append("ATRASO MÉDIO: ").append(atrasoMedio.setScale(0, RoundingMode.HALF_UP)).append(" dias - ");
        int numPedidos = cliente.getPedidos() != null ? cliente.getPedidos().size() : 0;
        sb.append(numPedidos).append(" PEDIDOS - ");
        BigDecimal ticketMedio = calcularTicketMedio(cliente);
        sb.append("TICKET MÉDIO: ").append(formatarCredito(ticketMedio));
        sb.append("\n");

        // LINE 4: EXPOSIÇÃO: LIMITE GRUPO: R$XXX - EM USO: R$XXX (XX%) - DISPONÍVEL: R$XXX
        sb.append("EXPOSIÇÃO: ");
        BigDecimal limiteGrupo = grupo.getLimiteAprovado() != null ? grupo.getLimiteAprovado() : BigDecimal.ZERO;
        BigDecimal limiteDisponivel = grupo.getLimiteDisponivel() != null ? grupo.getLimiteDisponivel() : BigDecimal.ZERO;
        BigDecimal emUso = limiteGrupo.subtract(limiteDisponivel);

        sb.append("LIMITE GRUPO: ").append(formatarCredito(limiteGrupo)).append(" - ");
        sb.append("EM USO: ").append(formatarCredito(emUso));

        // Calculate percentage (avoid division by zero)
        if (limiteGrupo.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentual = emUso.divide(limiteGrupo, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP);
            sb.append(" (").append(percentual).append("%)");
        } else {
            sb.append(" (0%)");
        }

        sb.append(" - DISPONÍVEL: ").append(formatarCredito(limiteDisponivel));
        sb.append("\n");

        // LINE 5: ANÁLISE: ALERTA1 | ALERTA2 | ... (or "SEM ALERTAS")
        sb.append("ANÁLISE: ");
        List<String> alertas = alertaService.calcularAlertas(pedido);
        if (alertas.isEmpty()) {
            sb.append("SEM ALERTAS");
        } else {
            sb.append(String.join(" | ", alertas));
        }
        sb.append("\n");

        // LINE 6: DECISÃO: APROVAR R$XXX (XX% do solicitado) [ONLY if limiteAprovado != pedido.valor]
        if (analise.getLimiteAprovado() != null &&
            analise.getLimiteAprovado().compareTo(pedido.getValor()) != 0) {
            sb.append("DECISÃO: APROVAR ").append(formatarCredito(analise.getLimiteAprovado()));

            // Calculate percentage of requested
            if (pedido.getValor().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentualSolicitado = analise.getLimiteAprovado()
                        .divide(pedido.getValor(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(0, RoundingMode.HALF_UP);
                sb.append(" (").append(percentualSolicitado).append("% do solicitado)");
            }
            sb.append("\n");
        }

        // LINE 7: FUNDAMENTO: [observações do analista]
        sb.append("FUNDAMENTO: ");
        if (analise.getObservacoes() != null && !analise.getObservacoes().trim().isEmpty()) {
            sb.append(analise.getObservacoes());
        } else {
            sb.append("Análise em andamento");
        }
        sb.append("\n");

        // LINE 8: ANALISTA: FINANCEIRO/COMERCIAL - APROVADOR: Gestor Crédito [if requerAprovacaoGestor]
        sb.append("ANALISTA: ");
        sb.append(analise.getTipoAnalista() != null ? analise.getTipoAnalista() : "SISTEMA");
        if (Boolean.TRUE.equals(analise.getRequerAprovacaoGestor())) {
            sb.append(" - APROVADOR: Gestor Crédito");
        }

        return sb.toString();
    }

    /**
     * Calcula atraso médio do cliente baseado em duplicatas.
     *
     * @param cliente Cliente para calcular atraso médio
     * @return Atraso médio em dias (0 se sem duplicatas ou sem atrasos)
     */
    private BigDecimal calcularAtrasoMedio(Cliente cliente) {
        List<Duplicata> duplicatas = cliente.getDuplicatas();
        if (duplicatas == null || duplicatas.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal somaAtrasos = duplicatas.stream()
                .map(Duplicata::getAtraso)
                .filter(atraso -> atraso != null && atraso > 0)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long duplicatasComAtraso = duplicatas.stream()
                .filter(d -> d.getAtraso() != null && d.getAtraso() > 0)
                .count();

        if (duplicatasComAtraso == 0) {
            return BigDecimal.ZERO;
        }

        return somaAtrasos.divide(BigDecimal.valueOf(duplicatasComAtraso), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula ticket médio (valor médio dos pedidos do cliente).
     *
     * @param cliente Cliente para calcular ticket médio
     * @return Ticket médio (0 se sem pedidos)
     */
    private BigDecimal calcularTicketMedio(Cliente cliente) {
        List<Pedido> pedidos = cliente.getPedidos();
        if (pedidos == null || pedidos.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal soma = pedidos.stream()
                .map(Pedido::getValor)
                .filter(valor -> valor != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return soma.divide(BigDecimal.valueOf(pedidos.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Obtém o score interno do grupo (mais recente DadosBI).
     *
     * @param grupoEconomicoId ID do grupo econômico
     * @return Score interno ou "N/A" se não encontrado
     */
    private String obterScoreInterno(Long grupoEconomicoId) {
        List<DadosBI> dadosBI = dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(grupoEconomicoId);

        if (dadosBI.isEmpty()) {
            return "N/A";
        }

        DadosBI maisRecente = dadosBI.get(0);
        return maisRecente.getScore() != null ? String.valueOf(maisRecente.getScore()) : "N/A";
    }

    /**
     * Extrai o tipo de empresa a partir da razão social.
     *
     * @param razaoSocial Razão social do cliente
     * @return Tipo identificado (LTDA, MEI, S/A, EIRELI, OUTROS)
     */
    String extrairTipo(String razaoSocial) {
        if (razaoSocial == null) return "N/D";
        if (razaoSocial.contains("LTDA")) return "LTDA";
        if (razaoSocial.contains("MEI")) return "MEI";
        if (razaoSocial.contains("EIRELI")) return "EIRELI";
        if (razaoSocial.contains("S/A")) return "S/A";
        if (razaoSocial.contains(" SA")) return "S/A";
        return "OUTROS";
    }

    /**
     * Formata valor de crédito em formato compacto (K para milhares, M para milhões).
     *
     * @param valor Valor a formatar
     * @return String formatada (ex: R$45K, R$1.5M)
     */
    String formatarCredito(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) return "N/D";

        if (valor.compareTo(new BigDecimal("1000")) < 0) {
            return "R$" + valor.intValue();
        } else if (valor.compareTo(new BigDecimal("1000000")) < 0) {
            return "R$" + (valor.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP)) + "K";
        } else {
            return "R$" + (valor.divide(new BigDecimal("1000000"), 1, RoundingMode.HALF_UP)) + "M";
        }
    }
}
