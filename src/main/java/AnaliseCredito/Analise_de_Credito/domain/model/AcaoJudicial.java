package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ação Judicial - Registro de ações judiciais em andamento ou encerradas.
 */
@Entity
@Table(name = "acao_judicial", indexes = {
    @Index(name = "idx_acao_judicial_cliente", columnList = "cliente_id"),
    @Index(name = "idx_acao_judicial_data", columnList = "data")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcaoJudicial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /**
     * Quantidade de ações judiciais
     */
    @Column(nullable = false)
    private Integer quantidade = 0;

    /**
     * Valor total das ações
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data da ação ou última atualização
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate data;
}
