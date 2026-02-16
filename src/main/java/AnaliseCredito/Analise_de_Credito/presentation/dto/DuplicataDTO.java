package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para importação de dados de duplicatas do arquivo XLSX.
 *
 * Colunas esperadas:
 * cnpj, posicao, portador, vencimento, valor, saldo, data_pagamento
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicataDTO {
    private String cnpj;
    private String posicao; // PROTESTO, CARTORIO, NEGATIVACAO, COBRANCA, CARTEIRA
    private String portador;
    private LocalDate vencimento;
    private BigDecimal valor;
    private BigDecimal saldo;
    private LocalDate dataPagamento;
}
