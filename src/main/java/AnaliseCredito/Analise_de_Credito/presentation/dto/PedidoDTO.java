package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para importação de dados de pedidos do arquivo XLSX.
 *
 * Colunas esperadas:
 * numero, data, valor, cnpj_cliente, marca, bloqueio,
 * deposito, condicao_pagamento, colecao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDTO {
    private String numero;
    private LocalDate data;
    private BigDecimal valor;
    private String cnpjCliente;
    private String marca;
    private String bloqueio; // "80" ou "36" = CLIENTE_NOVO
    private String deposito;
    private String condicaoPagamento;
    private Integer colecao; // AAAAMM format (ex: 202601)
}
