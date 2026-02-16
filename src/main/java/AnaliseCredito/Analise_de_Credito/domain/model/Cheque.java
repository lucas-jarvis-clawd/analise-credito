package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cheque - Registro de cheques sem fundo ou devolvidos.
 */
@Entity
@Table(name = "cheque", indexes = {
    @Index(name = "idx_cheque_cliente", columnList = "cliente_id"),
    @Index(name = "idx_cheque_data", columnList = "data")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cheque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /**
     * Quantidade de cheques devolvidos
     */
    @Column(nullable = false)
    private Integer quantidade = 0;

    /**
     * Valor total dos cheques
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data da ocorrência ou última atualização
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate data;
}
