package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Grupo Econômico - Agrupamento de clientes relacionados.
 *
 * REGRA CRÍTICA: Todo cliente DEVE pertencer a um grupo.
 * Se o cliente não possui grupo real, criar singleton com codigo=cnpj.
 *
 * Limites são SEMPRE armazenados no grupo, nunca no cliente individual.
 */
@Entity
@Table(name = "grupo_economico", indexes = {
    @Index(name = "idx_grupo_codigo", columnList = "codigo")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrupoEconomico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Código do grupo - pode ser CNPJ único (singleton) ou código customizado
     */
    @NotBlank
    @Column(nullable = false, length = 50)
    private String codigo;

    /**
     * Nome/descrição do grupo econômico
     */
    @NotBlank
    @Column(nullable = false, length = 200)
    private String nome;

    /**
     * Limite total aprovado para o grupo (soma de todos os clientes)
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteAprovado = BigDecimal.ZERO;

    /**
     * Limite disponível atual (limiteAprovado - total de pedidos abertos)
     */
    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteDisponivel = BigDecimal.ZERO;

    /**
     * Clientes pertencentes a este grupo
     */
    @OneToMany(mappedBy = "grupoEconomico", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Cliente> clientes = new ArrayList<>();

    /**
     * Dados de BI importados para este grupo
     */
    @OneToMany(mappedBy = "grupoEconomico", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DadosBI> dadosBI = new ArrayList<>();

    // ========== Métodos auxiliares (não JPA) ==========

    /**
     * Calcula total de pedidos em aberto de todos os clientes do grupo
     */
    @Transient
    public BigDecimal getTotalPedidosAbertos() {
        // TODO: Implementar via repository query
        throw new UnsupportedOperationException("Implementar via service/repository");
    }

    /**
     * Conta quantos clientes SIMEI existem no grupo
     */
    @Transient
    public Integer getCountSimeis() {
        return (int) clientes.stream()
            .filter(c -> Boolean.TRUE.equals(c.getSimei()))
            .count();
    }

    /**
     * Verifica se é um grupo real (múltiplos clientes) ou singleton
     */
    @Transient
    public boolean isGrupoReal() {
        return clientes != null && clientes.size() > 1;
    }
}
