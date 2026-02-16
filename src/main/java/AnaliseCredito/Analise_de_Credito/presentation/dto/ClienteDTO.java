package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para importação de dados de clientes do arquivo XLSX.
 *
 * Colunas esperadas:
 * cnpj, razao_social, nome_fantasia, telefone, email, estado,
 * tipo, data_fundacao, simei, situacao_credito, situacao_cobranca,
 * cluster, grupo_economico, score_boa_vista, score_boa_vista_data, sintegra
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDTO {
    private String cnpj;
    private String razaoSocial;
    private String nomeFantasia;
    private String telefone;
    private String email;
    private String estado;
    private String tipo; // BASE_PRAZO, NOVO, ANTECIPADO
    private LocalDate dataFundacao;
    private Boolean simei;
    private String situacaoCredito;
    private String situacaoCobranca;
    private String cluster;
    private String grupoEconomico; // Código do grupo
    private Integer scoreBoaVista;
    private LocalDate scoreBoaVistaData;
    private String sintegra;
}
