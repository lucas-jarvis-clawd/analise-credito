package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acao Judicial - Registro de acao judicial em andamento ou encerrada.
 * Cada registro representa uma ocorrencia individual, inserida manualmente pelo analista.
 */
@Entity
@Table(name = "acao_judicial", indexes = {
    @Index(name = "idx_acao_judicial_cliente", columnList = "cliente_id"),
    @Index(name = "idx_acao_judicial_data", columnList = "data_distribuicao")
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
     * Tipo da acao (ex: "Execucao Fiscal", "Cobranca", "Falencia")
     */
    @Column(length = 200)
    private String tipo;

    /**
     * Vara onde tramita a acao
     */
    @Column(length = 200)
    private String vara;

    /**
     * Valor da acao judicial
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    /**
     * Data de distribuicao da acao
     */
    @Column(name = "data_distribuicao")
    private LocalDate dataDistribuicao;
}
