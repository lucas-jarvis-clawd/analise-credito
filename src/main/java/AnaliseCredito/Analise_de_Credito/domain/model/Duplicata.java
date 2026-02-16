package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.PosicaoDuplicata;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Duplicata - Fatura de venda a prazo.
 *
 * REGRA CRÍTICA: Atraso é CALCULADO dinamicamente via getAtraso(),
 * NÃO é coluna armazenada no banco.
 */
@Entity
@Table(name = "duplicata", indexes = {
    @Index(name = "idx_duplicata_cliente", columnList = "cliente_id"),
    @Index(name = "idx_duplicata_vencimento", columnList = "vencimento"),
    @Index(name = "idx_duplicata_posicao", columnList = "posicao")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Duplicata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Relacionamento ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ========== Dados da Duplicata ==========

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosicaoDuplicata posicao;

    @Column(length = 100)
    private String portador;

    @NotNull
    @Column(nullable = false)
    private LocalDate vencimento;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    /**
     * Data de pagamento - null se ainda não pago
     */
    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    // ========== Métodos auxiliares ==========

    /**
     * MÉTODO CALCULADO - NÃO É COLUNA DO BANCO.
     *
     * Calcula atraso em dias:
     * - Se não vencido: retorna 0
     * - Se vencido e não pago: dias entre vencimento e hoje
     * - Se vencido e pago: dias entre vencimento e data de pagamento
     *
     * @return Número de dias de atraso (0 se não atrasado)
     */
    @Transient
    public Integer getAtraso() {
        if (vencimento == null) return 0;

        LocalDate hoje = LocalDate.now();

        // Se ainda não venceu, não há atraso
        if (vencimento.isAfter(hoje) || vencimento.isEqual(hoje)) {
            return 0;
        }

        // Vencido - calcular atraso
        if (dataPagamento == null) {
            // Não pago: atraso até hoje
            return (int) ChronoUnit.DAYS.between(vencimento, hoje);
        } else {
            // Pago: atraso foi até a data de pagamento
            if (dataPagamento.isBefore(vencimento) || dataPagamento.isEqual(vencimento)) {
                return 0; // Pago antes ou no vencimento
            }
            return (int) ChronoUnit.DAYS.between(vencimento, dataPagamento);
        }
    }

    /**
     * Verifica se duplicata está em atraso
     */
    @Transient
    public boolean isEmAtraso() {
        return getAtraso() > 0;
    }

    /**
     * Verifica se duplicata está vencida e não paga
     */
    @Transient
    public boolean isVencidaNaoPaga() {
        return vencimento.isBefore(LocalDate.now()) && dataPagamento == null;
    }

    /**
     * Verifica se duplicata está paga
     */
    @Transient
    public boolean isPaga() {
        return dataPagamento != null;
    }
}
