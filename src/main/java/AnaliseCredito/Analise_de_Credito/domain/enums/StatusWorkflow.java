package AnaliseCredito.Analise_de_Credito.domain.enums;

/**
 * Status do workflow de análise de crédito.
 * Representa os estados possíveis durante o processo de análise.
 * Alguns estados são específicos para determinados tipos de workflow.
 */
public enum StatusWorkflow {
    // Shared states
    PENDENTE,
    PARECER_APROVADO,
    PARECER_REPROVADO,
    AGUARDANDO_APROVACAO_GESTOR,
    REANALISE_COMERCIAL_SOLICITADA,
    REANALISADO_APROVADO,
    REANALISADO_REPROVADO,
    FINALIZADO,

    // BASE_PRAZO specific
    EM_ANALISE_FINANCEIRO,

    // CLIENTE_NOVO legacy (kept for compatibility)
    DOCUMENTACAO_SOLICITADA,
    DOCUMENTACAO_ENVIADA,

    // CLIENTE_NOVO pipeline states
    FAZER_CONSULTAS,
    SOLICITAR_CANCELAMENTO,
    CONSULTA_PROTESTOS,
    VERIFICACAO_LOJA_FISICA,
    CONSULTA_SCORE_RESTRICOES,
    ENCAMINHADO_ANTECIPADO,
    EM_ANALISE_CLIENTE_NOVO
}
