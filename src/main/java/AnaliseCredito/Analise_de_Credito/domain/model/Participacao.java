package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Participação - Registro de participação societária em outras empresas.
 * Mostra onde os sócios do cliente possuem participações em outras empresas.
 */
@Entity
@Table(name = "participacao", indexes = {
    @Index(name = "idx_participacao_cliente", columnList = "cliente_id"),
    @Index(name = "idx_participacao_empresa_cnpj", columnList = "empresa_cnpj")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Participacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Relacionamento ==========

    /**
     * Cliente cujos sócios participam de outras empresas
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ========== Dados da Empresa Participada ==========

    @NotBlank
    @Column(name = "empresa_nome", nullable = false, length = 200)
    private String empresaNome;

    /**
     * CNPJ da empresa onde há participação - 14 dígitos
     */
    @NotBlank
    @Size(min = 14, max = 14)
    @Column(name = "empresa_cnpj", nullable = false, length = 14)
    private String empresaCnpj;

    /**
     * Percentual de participação do sócio (0-100)
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentual = BigDecimal.ZERO;

    // ========== Métodos auxiliares ==========

    /**
     * Verifica se é participação significativa (> 25%)
     */
    @Transient
    public boolean isParticipacaoSignificativa() {
        return percentual != null && percentual.compareTo(new BigDecimal("25")) > 0;
    }

    /**
     * Retorna CNPJ formatado (XX.XXX.XXX/XXXX-XX)
     */
    @Transient
    public String getEmpresaCnpjFormatado() {
        if (empresaCnpj == null || empresaCnpj.length() != 14) return empresaCnpj;
        return empresaCnpj.substring(0, 2) + "." +
               empresaCnpj.substring(2, 5) + "." +
               empresaCnpj.substring(5, 8) + "/" +
               empresaCnpj.substring(8, 12) + "-" +
               empresaCnpj.substring(12, 14);
    }
}
