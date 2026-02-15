package AnaliseCredito.Analise_de_Credito.domain.enums;

/**
 * Status do workflow de análise de crédito.
 * Representa os estados possíveis durante o processo de análise.
 * Alguns estados são específicos para determinados tipos de workflow.
 */
public enum StatusWorkflow {
    PENDENTE,
    EM_ANALISE_FINANCEIRO,
    DOCUMENTACAO_SOLICITADA,
    DOCUMENTACAO_ENVIADA,
    PARECER_APROVADO,
    PARECER_REPROVADO,
    AGUARDANDO_APROVACAO_GESTOR,
    REANALISE_COMERCIAL_SOLICITADA,
    REANALISADO_APROVADO,
    REANALISADO_REPROVADO,
    FINALIZADO
}
