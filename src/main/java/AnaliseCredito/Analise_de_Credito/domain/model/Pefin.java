package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pefin - Restricao de credito PEFIN (Serasa).
 * Cada registro representa uma ocorrencia individual, inserida manualmente pelo analista.
 */
@Entity
@Table(name = "pefin", indexes = {
    @Index(name = "idx_pefin_cliente", columnList = "cliente_id"),
    @Index(name = "idx_pefin_data", columnList = "data_ocorrencia")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pefin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /**
     * Origem da restricao (ex: "Serasa", "SPC", "Banco X")
     */
    @Column(length = 200)
    private String origem;

    /**
     * Valor da restricao
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
