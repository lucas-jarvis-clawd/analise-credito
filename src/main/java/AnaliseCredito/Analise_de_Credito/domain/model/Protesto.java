package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Protesto - Registro de títulos protestados em cartório.
 */
@Entity
@Table(name = "protesto", indexes = {
    @Index(name = "idx_protesto_cliente", columnList = "cliente_id"),
    @Index(name = "idx_protesto_data", columnList = "data")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Protesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /**
     * Quantidade de protestos
     */
    @Column(nullable = false)
    private Integer quantidade = 0;

    /**
     * Valor total dos protestos
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data do protesto ou última atualização
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate data;
}
