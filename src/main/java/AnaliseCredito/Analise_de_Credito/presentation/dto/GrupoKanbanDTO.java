package AnaliseCredito.Analise_de_Credito.presentation.dto;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para consolidação de informações do grupo econômico no Kanban.
 * Agrupa dados de um grupo econômico e seus clientes/análises para exibição
 * na visualização Kanban interativa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoKanbanDTO {

    private Long grupoEconomicoId;

    private String grupoNome;

    private String grupoCodigo;

    private List<String> clientesRazaoSocial;

    private List<Long> analiseIds;

    private Integer quantidadePedidos;

    private BigDecimal valorTotal;

    private Integer piorScore;

    private List<String> alertasConsolidados;

    private StatusWorkflow status;

    private TipoWorkflow workflow;

    private Long analisePrincipalId;
}
