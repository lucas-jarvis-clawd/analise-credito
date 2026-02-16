package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pefin - Restrição de crédito PEFIN (Serasa).
 * Registro de pendências financeiras cadastradas.
 */
@Entity
@Table(name = "pefin", indexes = {
    @Index(name = "idx_pefin_cliente", columnList = "cliente_id"),
    @Index(name = "idx_pefin_data", columnList = "data")
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
     * Quantidade de ocorrências
     */
    @Column(nullable = false)
    private Integer quantidade = 0;

    /**
     * Valor total das restrições
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data da última atualização/registro
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate data;
}
