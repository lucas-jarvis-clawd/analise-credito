package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para importação de dados de BI do arquivo XLSX.
 *
 * Colunas esperadas:
 * grupo_economico, colecao, valor_vencido, credito, score, atraso_medio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DadosBIDTO {
    private String grupoEconomico; // Código do grupo
    private Integer colecao; // AAAAMM format
    private BigDecimal valorVencido;
    private BigDecimal credito;
    private Integer score;
    private BigDecimal atrasoMedio;
}
