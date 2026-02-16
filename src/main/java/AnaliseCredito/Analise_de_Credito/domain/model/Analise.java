package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoAnalista;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Análise de Crédito - Registro completo do processo de análise.
 *
 * Armazena todas as informações da análise, incluindo decisões,
 * justificativas, pareceres e histórico do workflow.
 *
 * IMPORTANTE: Relaciona-se bidirecional com Pedido via @OneToOne.
 * Para evitar loops, referências a Cliente e GrupoEconomico são via ID.
 */
@Entity
@Table(name = "analise", indexes = {
    @Index(name = "idx_analise_pedido", columnList = "pedido_id"),
    @Index(name = "idx_analise_cliente", columnList = "cliente_id"),
    @Index(name = "idx_analise_status", columnList = "status_workflow"),
    @Index(name = "idx_analise_data", columnList = "data_inicio")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Analise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Referências (evitar loops de serialização) ==========

    /**
     * Relacionamento bidirecional com Pedido
     */
    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    /**
     * Referência ao cliente (via ID para evitar loops)
     */
    @NotNull
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /**
     * Referência ao grupo econômico (via ID para evitar loops)
     */
    @NotNull
    @Column(name = "grupo_economico_id", nullable = false)
    private Long grupoEconomicoId;

    // ========== Workflow ==========

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_workflow", nullable = false, length = 50)
    private StatusWorkflow statusWorkflow = StatusWorkflow.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_analista", length = 20)
    private TipoAnalista tipoAnalista;

    // ========== Decisão ==========

    /**
     * Decisão final: APROVADO, REPROVADO, LIMITADO, etc.
     */
    @Column(length = 50)
    private String decisao;

    @Column(name = "limite_aprovado", precision = 15, scale = 2)
    private BigDecimal limiteAprovado;

    @Column(name = "limite_sugerido", precision = 15, scale = 2)
    private BigDecimal limiteSugerido;

    @Column(length = 1000)
    private String justificativa;

    @Column(length = 2000)
    private String observacoes;

    // ========== Metadados ==========

    @NotNull
    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    @Column(name = "analista_responsavel", length = 100)
    private String analistaResponsavel;

    /**
     * Snapshot do score Boa Vista no momento da análise
     */
    @Column(name = "score_no_momento")
    private Integer scoreNoMomento;

    // ========== Geração de Pareceres e E-mails ==========

    /**
     * Parecer gerado pelo sistema (template processado)
     */
    @Column(name = "parecer_template", length = 500)
    private String parecerTemplate;

    /**
     * Parecer específico para CRM - APENAS workflow CLIENTE_NOVO
     */
    @Column(name = "parecer_crm", length = 500)
    private String parecerCRM;

    /**
     * E-mail gerado automaticamente para notificação
     */
    @Column(name = "email_gerado", length = 2000)
    private String emailGerado;

    // ========== Alçada ==========

    /**
     * Indica se análise requer aprovação de gestor
     */
    @Column(name = "requer_aprovacao_gestor", nullable = false)
    private Boolean requerAprovacaoGestor = false;

    // ========== Métodos auxiliares ==========

    /**
     * Verifica se análise está finalizada
     */
    @Transient
    public boolean isFinalizada() {
        return StatusWorkflow.FINALIZADO.equals(statusWorkflow) && dataFim != null;
    }

    /**
     * Verifica se está aguardando ação
     */
    @Transient
    public boolean isAguardandoAcao() {
        return StatusWorkflow.DOCUMENTACAO_SOLICITADA.equals(statusWorkflow) ||
               StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR.equals(statusWorkflow);
    }

    /**
     * Calcula duração da análise em horas
     */
    @Transient
    public Long getDuracaoHoras() {
        if (dataInicio == null) return 0L;
        LocalDateTime fim = dataFim != null ? dataFim : LocalDateTime.now();
        return java.time.Duration.between(dataInicio, fim).toHours();
    }
}
