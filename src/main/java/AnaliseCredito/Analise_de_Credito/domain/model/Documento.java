package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoAnalista;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoDocumento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Documento - Arquivo anexado ao processo de análise de crédito.
 * Pode ser IR de sócio, Nota Fiscal, ou outros documentos solicitados.
 */
@Entity
@Table(name = "documento", indexes = {
    @Index(name = "idx_documento_cliente", columnList = "cliente_id"),
    @Index(name = "idx_documento_tipo", columnList = "tipo"),
    @Index(name = "idx_documento_upload", columnList = "data_upload")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Relacionamento ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ========== Dados do Documento ==========

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumento tipo;

    @NotBlank
    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    /**
     * Caminho relativo ou absoluto do arquivo no sistema de arquivos
     */
    @NotBlank
    @Column(name = "caminho_arquivo", nullable = false, length = 500)
    private String caminhoArquivo;

    @NotNull
    @Column(name = "data_upload", nullable = false)
    private LocalDateTime dataUpload;

    /**
     * Tipo de analista que fez o upload (FINANCEIRO ou COMERCIAL)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_por", length = 20)
    private TipoAnalista uploadPor;

    // ========== Métodos auxiliares ==========

    /**
     * Retorna extensão do arquivo
     */
    @Transient
    public String getExtensao() {
        if (nomeArquivo == null || !nomeArquivo.contains(".")) return "";
        return nomeArquivo.substring(nomeArquivo.lastIndexOf(".") + 1).toUpperCase();
    }

    /**
     * Verifica se é um arquivo de imagem
     */
    @Transient
    public boolean isImagem() {
        String ext = getExtensao();
        return ext.equals("JPG") || ext.equals("JPEG") || ext.equals("PNG") || ext.equals("GIF");
    }

    /**
     * Verifica se é um PDF
     */
    @Transient
    public boolean isPdf() {
        return "PDF".equals(getExtensao());
    }

    /**
     * Retorna tamanho legível do caminho
     */
    @Transient
    public String getNomeArquivoEncurtado() {
        if (nomeArquivo == null || nomeArquivo.length() <= 30) return nomeArquivo;
        return nomeArquivo.substring(0, 27) + "...";
    }
}
