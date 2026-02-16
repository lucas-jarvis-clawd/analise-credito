package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sócio - Pessoa física sócia da empresa cliente.
 * Utilizado para análise de capacidade financeira e garantias.
 */
@Entity
@Table(name = "socio", indexes = {
    @Index(name = "idx_socio_cliente", columnList = "cliente_id"),
    @Index(name = "idx_socio_cpf", columnList = "cpf")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Socio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Relacionamento ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ========== Dados do Sócio ==========

    @NotBlank
    @Column(nullable = false, length = 200)
    private String nome;

    /**
     * CPF - 11 dígitos
     */
    @NotBlank
    @Size(min = 11, max = 11)
    @Column(nullable = false, length = 11)
    private String cpf;

    /**
     * Percentual de participação (0-100)
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal participacao = BigDecimal.ZERO;

    /**
     * Renda anual declarada
     */
    @Column(name = "renda_anual", precision = 15, scale = 2)
    private BigDecimal rendaAnual = BigDecimal.ZERO;

    // ========== Métodos auxiliares ==========

    /**
     * Verifica se é sócio majoritário (> 50%)
     */
    @Transient
    public boolean isMajoritario() {
        return participacao != null && participacao.compareTo(new BigDecimal("50")) > 0;
    }

    /**
     * Retorna CPF formatado (XXX.XXX.XXX-XX)
     */
    @Transient
    public String getCpfFormatado() {
        if (cpf == null || cpf.length() != 11) return cpf;
        return cpf.substring(0, 3) + "." +
               cpf.substring(3, 6) + "." +
               cpf.substring(6, 9) + "-" +
               cpf.substring(9, 11);
    }
}
