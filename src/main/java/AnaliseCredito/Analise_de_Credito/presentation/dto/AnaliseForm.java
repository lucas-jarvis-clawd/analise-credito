package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AnaliseForm - DTO para capturar decisão do analista.
 *
 * Usado no POST /analise/{id}/concluir para registrar a decisão
 * final da análise de crédito.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnaliseForm {

    /**
     * Decisão final do analista.
     * Valores aceitos: APROVADO, LIMITADO, REPROVADO
     */
    private String decisao;

    /**
     * Limite aprovado quando decisão = LIMITADO.
     * Obrigatório apenas nesse caso.
     */
    private BigDecimal limiteAprovado;

    /**
     * Justificativa da decisão (obrigatória).
     */
    private String justificativa;
}
