package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.TendenciaRisco;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // ========== Análise de Tendência ==========

    /**
     * Calcula variação de score em relação à coleção anterior.
     * Requer que o grupoEconomico esteja carregado com seus dadosBI.
     *
     * @return Variação do score (positivo = melhora, negativo = piora), ou null se não houver histórico
     */
    @Transient
    public Integer getVariacaoScore() {
        if (grupoEconomico == null || score == null) {
            return null;
        }

        // Buscar coleção anterior
        DadosBI colecaoAnterior = findColecaoAnterior();
        if (colecaoAnterior == null || colecaoAnterior.getScore() == null) {
            return null;
        }

        return score - colecaoAnterior.getScore();
    }

    /**
     * Calcula variação de atraso médio em relação à coleção anterior.
     * Requer que o grupoEconomico esteja carregado com seus dadosBI.
     *
     * @return Variação do atraso (positivo = piora, negativo = melhora), ou null se não houver histórico
     */
    @Transient
    public BigDecimal getVariacaoAtraso() {
        if (grupoEconomico == null || atrasoMedio == null) {
            return null;
        }

        DadosBI colecaoAnterior = findColecaoAnterior();
        if (colecaoAnterior == null || colecaoAnterior.getAtrasoMedio() == null) {
            return null;
        }

        return atrasoMedio.subtract(colecaoAnterior.getAtrasoMedio());
    }

    /**
     * Determina tendência de risco baseada em variações de score e atraso.
     *
     * @return MELHORANDO, ESTAVEL ou DETERIORANDO
     */
    @Transient
    public TendenciaRisco getTendencia() {
        Integer variacaoScore = getVariacaoScore();
        BigDecimal variacaoAtraso = getVariacaoAtraso();

        // Se não houver dados históricos, considerar estável
        if (variacaoScore == null && variacaoAtraso == null) {
            return TendenciaRisco.ESTAVEL;
        }

        // Thresholds para considerar mudança significativa
        int scoreThreshold = 50;  // Variação mínima de score para considerar tendência
        BigDecimal atrasoThreshold = new BigDecimal("5");  // Variação mínima de atraso (dias)

        boolean scoreSubindo = variacaoScore != null && variacaoScore > scoreThreshold;
        boolean scoreCaindo = variacaoScore != null && variacaoScore < -scoreThreshold;
        boolean atrasoDiminuindo = variacaoAtraso != null && variacaoAtraso.compareTo(atrasoThreshold.negate()) < 0;
        boolean atrasoAumentando = variacaoAtraso != null && variacaoAtraso.compareTo(atrasoThreshold) > 0;

        // Lógica de decisão
        if (scoreSubindo || atrasoDiminuindo) {
            // Se pelo menos um indicador está melhorando
            if (!scoreCaindo && !atrasoAumentando) {
                return TendenciaRisco.MELHORANDO;
            }
        }

        if (scoreCaindo || atrasoAumentando) {
            // Se pelo menos um indicador está piorando
            if (!scoreSubindo && !atrasoDiminuindo) {
                return TendenciaRisco.DETERIORANDO;
            }
        }

        return TendenciaRisco.ESTAVEL;
    }

    /**
     * Busca coleção anterior deste grupo econômico.
     * Assume que dadosBI do grupo estão carregados.
     */
    private DadosBI findColecaoAnterior() {
        if (grupoEconomico == null || colecao == null) {
            return null;
        }

        // Buscar nos dadosBI do grupo a coleção imediatamente anterior
        List<DadosBI> todosDados = grupoEconomico.getDadosBI();
        if (todosDados == null || todosDados.isEmpty()) {
            return null;
        }

        DadosBI anterior = null;
        for (DadosBI dados : todosDados) {
            if (dados.getColecao() != null && dados.getColecao() < this.colecao) {
                if (anterior == null || dados.getColecao() > anterior.getColecao()) {
                    anterior = dados;
                }
            }
        }

        return anterior;
    }
}
