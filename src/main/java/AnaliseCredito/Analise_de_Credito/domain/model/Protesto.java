package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Protesto - Registro de titulo protestado em cartorio.
 * Cada registro representa uma ocorrencia individual, inserida manualmente pelo analista.
 */
@Entity
@Table(name = "protesto", indexes = {
    @Index(name = "idx_protesto_cliente", columnList = "cliente_id"),
    @Index(name = "idx_protesto_data", columnList = "data_protesto")
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
     * Nome do cartorio onde foi protestado
     */
    @Column(length = 200)
    private String cartorio;

    /**
     * Valor do protesto
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data do protesto
     */
    @Column(name = "data_protesto")
    private LocalDate dataProtesto;
}
