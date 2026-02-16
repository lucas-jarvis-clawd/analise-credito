package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Configuração - Parâmetros globais do sistema.
 *
 * PADRÃO SINGLETON: Deve existir apenas 1 registro (ID = 1).
 * Armazena limites, thresholds e multiplicadores usados nas regras de negócio.
 */
@Entity
@Table(name = "configuracao")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Configuracao {

    /**
     * ID fixo = 1 (única linha na tabela)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Limites SIMEI ==========

    /**
     * Limite padrão para empresas SIMEI
     * Default: 35000
     */
    @NotNull
    @Column(name = "limite_simei", nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteSimei = new BigDecimal("35000");

    /**
     * Número máximo de clientes SIMEI por grupo econômico
     * Default: 2
     */
    @NotNull
    @Column(name = "max_simeis_por_grupo", nullable = false)
    private Integer maxSimeisPorGrupo = 2;

    // ========== Thresholds de Score ==========

    /**
     * Score considerado baixo (< este valor)
     * Default: 300
     */
    @NotNull
    @Column(name = "score_baixo_threshold", nullable = false)
    private Integer scoreBaixoThreshold = 300;

    // ========== Multiplicadores de Score ==========

    /**
     * Multiplicador para score alto (>= 800)
     * Default: 1.5
     */
    @NotNull
    @Column(name = "score_alto_multiplicador", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreAltoMultiplicador = new BigDecimal("1.5");

    /**
     * Multiplicador para score médio (>= 600)
     * Default: 1.2
     */
    @NotNull
    @Column(name = "score_medio_multiplicador", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreMedioMultiplicador = new BigDecimal("1.2");

    /**
     * Multiplicador para score normal (>= 400)
     * Default: 1.0
     */
    @NotNull
    @Column(name = "score_normal_multiplicador", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreNormalMultiplicador = new BigDecimal("1.0");

    /**
     * Multiplicador para score baixo (< 400)
     * Default: 0.7
     */
    @NotNull
    @Column(name = "score_baixo_multiplicador", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreBaixoMultiplicador = new BigDecimal("0.7");

    // ========== Alçada (Aprovação de Gestor) ==========

    /**
     * Valor de pedido que requer aprovação de gestor
     * Default: 100000
     */
    @NotNull
    @Column(name = "valor_aprovacao_gestor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorAprovacaoGestor = new BigDecimal("100000");

    /**
     * Total de crédito do grupo que requer aprovação de gestor
     * Default: 200000
     */
    @NotNull
    @Column(name = "total_grupo_aprovacao_gestor", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalGrupoAprovacaoGestor = new BigDecimal("200000");

    /**
     * Número de restrições que requer aprovação de gestor
     * Default: 5
     */
    @NotNull
    @Column(name = "restricoes_aprovacao_gestor", nullable = false)
    private Integer restricoesAprovacaoGestor = 5;

    // ========== Pipeline Cliente Novo ==========

    @Column(name = "cnaes_permitidos", length = 2000)
    private String cnaesPermitidos;

    @Column(name = "protesto_threshold_antecipado", precision = 15, scale = 2)
    private BigDecimal protestoThresholdAntecipado = new BigDecimal("1000");

    @Column(name = "restricao_threshold_antecipado", precision = 15, scale = 2)
    private BigDecimal restricaoThresholdAntecipado = new BigDecimal("1000");

    @Column(name = "meses_loja_threshold")
    private Integer mesesLojaThreshold = 10;

    @Column(name = "meses_fundacao_threshold")
    private Integer mesesFundacaoThreshold = 12;

    // ========== Métodos auxiliares ==========

    /**
     * Retorna o multiplicador baseado no score
     */
    @Transient
    public BigDecimal getMultiplicadorPorScore(Integer score) {
        if (score == null) return scoreNormalMultiplicador;

        if (score >= 800) return scoreAltoMultiplicador;
        if (score >= 600) return scoreMedioMultiplicador;
        if (score >= 400) return scoreNormalMultiplicador;
        return scoreBaixoMultiplicador;
    }

    /**
     * Verifica se score é considerado baixo
     */
    @Transient
    public boolean isScoreBaixo(Integer score) {
        return score != null && score < scoreBaixoThreshold;
    }

    /**
     * Verifica se valor requer aprovação de gestor
     */
    @Transient
    public boolean requerAprovacaoPorValor(BigDecimal valor) {
        return valor != null && valor.compareTo(valorAprovacaoGestor) > 0;
    }

    /**
     * Verifica se total do grupo requer aprovação de gestor
     */
    @Transient
    public boolean requerAprovacaoPorTotalGrupo(BigDecimal totalGrupo) {
        return totalGrupo != null && totalGrupo.compareTo(totalGrupoAprovacaoGestor) > 0;
    }

    /**
     * Verifica se número de restrições requer aprovação de gestor
     */
    @Transient
    public boolean requerAprovacaoPorRestricoes(Integer numRestricoes) {
        return numRestricoes != null && numRestricoes >= restricoesAprovacaoGestor;
    }

    /**
     * Verifica se um CNAE é permitido para o pipeline de cliente novo.
     * Se a lista de CNAEs permitidos estiver vazia/null, todos são permitidos.
     */
    @Transient
    public boolean isCnaePermitido(String cnae) {
        if (cnaesPermitidos == null || cnaesPermitidos.isBlank()) {
            return true;
        }
        if (cnae == null || cnae.isBlank()) {
            return false;
        }
        String[] permitidos = cnaesPermitidos.split(",");
        for (String permitido : permitidos) {
            if (permitido.trim().equals(cnae.trim())) {
                return true;
            }
        }
        return false;
    }
}
