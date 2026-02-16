package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * ParecerService - Gera parecer formatado para integração com CRM.
 *
 * REGRA CRÍTICA:
 * - APENAS gera parecer para workflow CLIENTE_NOVO (bloqueio 80/36)
 * - Retorna null para BASE_PRAZO
 *
 * Formato do Parecer CRM:
 * [DECISÃO] DATA - TIPO - FUNDAÇÃO - SIMEI - RESTRIÇÕES - CRED - SCORE - SÓCIOS - PARTS
 *
 * Exemplo:
 * [APROVADO] 15/02/2026 - LTDA - 05/2018 - SIM - 2 - R$45K - 720 - 2 SÓCIOS - 1 PART
 */
@Service
public class ParecerService {

    @Autowired
    private ClienteRepository clienteRepository;

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

        // 3. Build formatted string
        StringBuilder sb = new StringBuilder();

        // [DECISÃO]
        sb.append("[").append(analise.getDecisao() != null ? analise.getDecisao() : "EM ANÁLISE").append("] ");

        // DATA
        LocalDate data = analise.getDataFim() != null ?
                analise.getDataFim().toLocalDate() : LocalDate.now();
        sb.append(data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append(" - ");

        // TIPO (extract from razaoSocial)
        String tipo = extrairTipo(cliente.getRazaoSocial());
        sb.append(tipo).append(" - ");

        // FUNDAÇÃO
        if (cliente.getDataFundacao() != null) {
            sb.append(cliente.getDataFundacao().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        } else {
            sb.append("N/D");
        }
        sb.append(" - ");

        // SIMEI
        sb.append(Boolean.TRUE.equals(cliente.getSimei()) ? "SIM" : "NÃO").append(" - ");

        // RESTRIÇÕES
        int restricoes = cliente.getTotalRestricoes();
        sb.append(restricoes).append(" - ");

        // CRÉDITO (use limiteSugerido)
        String credito = formatarCredito(analise.getLimiteSugerido());
        sb.append(credito).append(" - ");

        // SCORE
        sb.append(cliente.getScoreBoaVista() != null ? cliente.getScoreBoaVista() : "N/D").append(" - ");

        // SÓCIOS
        sb.append(cliente.getSocios().size()).append(" SÓCIOS - ");

        // PARTICIPAÇÕES
        sb.append(cliente.getParticipacoes().size()).append(" PART");

        return sb.toString();
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
