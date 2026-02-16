package AnaliseCredito.Analise_de_Credito.domain.enums;

/**
 * Tendência de risco baseada em análise histórica de dados BI.
 */
public enum TendenciaRisco {
    MELHORANDO,    // Score subindo, atraso diminuindo
    ESTAVEL,       // Sem mudanças significativas
    DETERIORANDO   // Score caindo, atraso aumentando
}
