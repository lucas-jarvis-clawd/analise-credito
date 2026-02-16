package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cheque - Registro de cheque sem fundo ou devolvido.
 * Cada registro representa uma ocorrencia individual, inserida manualmente pelo analista.
 */
@Entity
@Table(name = "cheque", indexes = {
    @Index(name = "idx_cheque_cliente", columnList = "cliente_id"),
    @Index(name = "idx_cheque_data", columnList = "data_ocorrencia")
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
     * Nome do banco
     */
    @Column(length = 100)
    private String banco;

    /**
     * Numero da agencia
     */
    @Column(length = 20)
    private String agencia;

    /**
     * Valor do cheque
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data da ocorrencia
     */
    @Column(name = "data_ocorrencia")
    private LocalDate dataOcorrencia;
}
