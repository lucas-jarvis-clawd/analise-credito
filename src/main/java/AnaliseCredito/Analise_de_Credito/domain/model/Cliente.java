package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente - Entidade principal do sistema.
 *
 * REGRAS CRÍTICAS:
 * - Todo cliente DEVE ter grupoEconomico (não pode ser null)
 * - Limites são armazenados no GrupoEconomico, NÃO aqui
 * - Atrasos são calculados dinamicamente das Duplicatas via queries
 * - Flag SIMEI é importante para regras de limite
 */
@Entity
@Table(name = "cliente", indexes = {
    @Index(name = "idx_cliente_cnpj", columnList = "cnpj", unique = true),
    @Index(name = "idx_cliente_grupo", columnList = "grupo_economico_id"),
    @Index(name = "idx_cliente_tipo", columnList = "tipo_cliente")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Dados Cadastrais ==========

    @NotBlank
    @Size(min = 14, max = 14)
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String razaoSocial;

    @Column(length = 200)
    private String nomeFantasia;

    @Column(length = 20)
    private String telefone;

    @Email
    @Column(length = 100)
    private String email;

    /**
     * UF - sigla do estado (2 letras)
     */
    @Size(min = 2, max = 2)
    @Column(length = 2)
    private String estado;

    // ========== Classificação ==========

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCliente tipoCliente;

    /**
     * Flag SIMEI - importante para cálculo de limites
     */
    @Column(nullable = false)
    private Boolean simei = false;

    @Column(length = 50)
    private String cluster;

    @Column(length = 50)
    private String situacaoCredito;

    @Column(length = 50)
    private String situacaoCobranca;

    @Column(length = 50)
    private String sintegra;

    // ========== Pipeline Cliente Novo ==========

    @Column(name = "status_receita", length = 50)
    private String statusReceita;

    @Column(name = "status_simples", length = 50)
    private String statusSimples;

    @Column(length = 20)
    private String cnae;

    @Column(name = "data_abertura_loja")
    private LocalDate dataAberturaLoja;

    // ========== Datas ==========

    private LocalDate dataFundacao;

    // ========== Scores ==========

    /**
     * Score Boa Vista (0-1000)
     */
    @Column(name = "score_boa_vista")
    private Integer scoreBoaVista;

    @Column(name = "score_boa_vista_data")
    private LocalDate scoreBoaVistaData;

    // ========== Relacionamentos ==========

    /**
     * Grupo econômico - OBRIGATÓRIO
     */
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_economico_id", nullable = false)
    private GrupoEconomico grupoEconomico;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pedido> pedidos = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Documento> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Duplicata> duplicatas = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Socio> socios = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Participacao> participacoes = new ArrayList<>();

    // Restrições
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Pefin> pefins = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Protesto> protestos = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AcaoJudicial> acoesJudiciais = new ArrayList<>();

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Cheque> cheques = new ArrayList<>();

    // ========== Métodos auxiliares para atrasos (NÃO armazenados) ==========

    /**
     * Calcula maior atraso da última coleção importada
     * TODO: Implementar via repository query
     */
    @Transient
    public Integer getMaiorAtrasoUltimaColecao() {
        throw new UnsupportedOperationException("Implementar via service/repository");
    }

    /**
     * Calcula atraso atual (duplicatas vencidas não pagas)
     * TODO: Implementar via repository query
     */
    @Transient
    public Integer getAtrasoAtual() {
        throw new UnsupportedOperationException("Implementar via service/repository");
    }

    /**
     * Soma total de restrições cadastradas
     */
    @Transient
    public Integer getTotalRestricoes() {
        return pefins.size() + protestos.size() + acoesJudiciais.size() + cheques.size();
    }
}
