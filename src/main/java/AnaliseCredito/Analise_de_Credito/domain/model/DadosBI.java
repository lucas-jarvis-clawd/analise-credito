package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dados de Business Intelligence importados de sistemas externos.
 *
 * IMPORTANTE: Vinculado ao GRUPO ECONÔMICO, não ao cliente individual.
 * Cada linha representa dados consolidados de uma coleção específica.
 */
@Entity
@Table(name = "dados_bi", indexes = {
    @Index(name = "idx_dados_bi_grupo", columnList = "grupo_economico_id"),
    @Index(name = "idx_dados_bi_colecao", columnList = "colecao"),
    @Index(name = "idx_dados_bi_importacao", columnList = "data_importacao")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DadosBI {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Relacionamento ==========

    /**
     * Vinculado ao grupo econômico, NÃO ao cliente individual
     */
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_economico_id", nullable = false)
    private GrupoEconomico grupoEconomico;

    // ========== Dados da Coleção ==========

    /**
     * Coleção no formato AAAAMM (ex: 202601)
     */
    @NotNull
    @Column(nullable = false)
    private Integer colecao;

    /**
     * Valor total vencido na coleção
     */
    @Column(name = "valor_vencido", precision = 15, scale = 2)
    private BigDecimal valorVencido = BigDecimal.ZERO;

    /**
     * Crédito disponível na coleção
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal credito = BigDecimal.ZERO;

    /**
     * Score interno calculado (diferente do scoreBoaVista do Cliente)
     */
    private Integer score;

    /**
     * Atraso médio em dias
     */
    @Column(name = "atraso_medio", precision = 10, scale = 2)
    private BigDecimal atrasoMedio = BigDecimal.ZERO;

    // ========== Metadados ==========

    @NotNull
    @Column(name = "data_importacao", nullable = false)
    private LocalDateTime dataImportacao;

    // ========== Métodos auxiliares ==========

    /**
     * Verifica se dados são da última coleção
     */
    @Transient
    public boolean isUltimaColecao() {
        // TODO: Comparar com coleção atual do sistema
        throw new UnsupportedOperationException("Implementar comparação com coleção atual");
    }

    /**
     * Retorna string formatada da coleção (ex: "Jan/2026")
     */
    @Transient
    public String getColecaoFormatada() {
        if (colecao == null) return "";
        int ano = colecao / 100;
        int mes = colecao % 100;
        String[] meses = {"", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
                         "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
        return mes >= 1 && mes <= 12 ? meses[mes] + "/" + ano : colecao.toString();
    }
}
