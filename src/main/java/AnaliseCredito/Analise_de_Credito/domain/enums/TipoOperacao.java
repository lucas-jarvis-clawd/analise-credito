package AnaliseCredito.Analise_de_Credito.domain.enums;

/**
 * Tipo de Operação Comercial - classifica o risco do pedido por natureza.
 *
 * Usado para avaliar risco baseado no tipo de pedido:
 * - REPOSICAO: Baixo risco (cliente recompra produtos conhecidos)
 * - LANCAMENTO: Médio risco (nova coleção, produtos novos)
 * - OPORTUNIDADE: Alto risco (pedido pontual fora do padrão normal)
 */
public enum TipoOperacao {
    /**
     * Reposição - Cliente recompra produtos já conhecidos (baixo risco)
     */
    REPOSICAO,

    /**
     * Lançamento - Nova coleção com produtos novos (médio risco)
     */
    LANCAMENTO,

    /**
     * Oportunidade - Pedido pontual fora do padrão (alto risco)
     */
    OPORTUNIDADE
}
