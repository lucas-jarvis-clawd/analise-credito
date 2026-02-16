package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido - Solicitação de crédito para análise.
 *
 * REGRA CRÍTICA: Campo 'bloqueio' determina o workflow:
 * - bloqueio = "80" ou "36" → TipoWorkflow.CLIENTE_NOVO
 * - outros valores → TipoWorkflow.BASE_PRAZO
 */
@Entity
@Table(name = "pedido", indexes = {
    @Index(name = "idx_pedido_numero", columnList = "numero"),
    @Index(name = "idx_pedido_cliente", columnList = "cliente_id"),
    @Index(name = "idx_pedido_data", columnList = "data"),
    @Index(name = "idx_pedido_colecao", columnList = "colecao")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Dados do Pedido ==========

    @NotBlank
    @Column(nullable = false, length = 50)
    private String numero;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String deposito;

    @Column(name = "condicao_pagamento", length = 100)
    private String condicaoPagamento;

    /**
     * Coleção no formato AAAAMM (ex: 202601)
     */
    private Integer colecao;

    // ========== Bloqueio e Workflow ==========

    /**
     * Código de bloqueio que determina o tipo de workflow.
     * "80" ou "36" = CLIENTE_NOVO
     * Outros = BASE_PRAZO
     */
    @Column(length = 10)
    private String bloqueio;

    /**
     * Status atual da análise (string livre)
     */
    @Column(name = "status_analise", length = 100)
    private String statusAnalise;

    /**
     * Tipo de workflow calculado a partir do bloqueio
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoWorkflow workflow;

    // ========== Relacionamentos ==========

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Analise analise;

    // ========== Alertas (calculados dinamicamente, NÃO persistidos) ==========

    /**
     * Lista de alertas gerados durante análise.
     * Calculado em tempo real pelo AlertaService.
     */
    @Transient
    private List<String> alerts = new ArrayList<>();

    // ========== Métodos auxiliares ==========

    /**
     * Determina o workflow baseado no código de bloqueio
     */
    public void calcularWorkflow() {
        if ("80".equals(bloqueio) || "36".equals(bloqueio)) {
            this.workflow = TipoWorkflow.CLIENTE_NOVO;
        } else {
            this.workflow = TipoWorkflow.BASE_PRAZO;
        }
    }

    /**
     * Verifica se pedido é de cliente novo
     */
    @Transient
    public boolean isClienteNovo() {
        return TipoWorkflow.CLIENTE_NOVO.equals(workflow);
    }

    /**
     * Verifica se pedido está em análise
     */
    @Transient
    public boolean isEmAnalise() {
        return analise != null && analise.getDataFim() == null;
    }
}
