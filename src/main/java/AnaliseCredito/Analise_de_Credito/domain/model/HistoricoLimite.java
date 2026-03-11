package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Histórico de limites aprovados por GrupoEconomico.
 * Cada registro representa uma versão do limite com data e responsável.
 * O limite atual do grupo é sempre igual ao registro mais recente.
 */
@Entity
@Table(name = "historico_limite")
@Data
@NoArgsConstructor
public class HistoricoLimite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_economico_id", nullable = false)
    private GrupoEconomico grupoEconomico;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataRegistro;

    @NotNull
    @Column(nullable = false, length = 100)
    private String responsavel;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
    }
}
