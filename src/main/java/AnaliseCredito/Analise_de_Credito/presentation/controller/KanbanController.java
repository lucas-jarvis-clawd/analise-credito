package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.AlertaService;
import AnaliseCredito.Analise_de_Credito.application.service.WorkflowService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AnaliseRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import AnaliseCredito.Analise_de_Credito.presentation.dto.GrupoKanbanDTO;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KanbanController - Controlador do dashboard Kanban de análises.
 *
 * Responsabilidades:
 * 1. Exibir dashboard com análises agrupadas por GRUPO ECONÔMICO, workflow e status
 * 2. Filtrar análises por tipo (BASE_PRAZO, CLIENTE_NOVO, TODOS)
 * 3. Calcular alertas consolidados para cada grupo
 * 4. Gerenciar drag-and-drop de cards entre colunas via HTMX
 * 5. Atualizar status de análises via transições do workflow
 *
 * IMPORTANTE: Este é o coração da aplicação - onde os analistas visualizam
 * e gerenciam o pipeline de análise de crédito.
 *
 * MUDANÇA ARQUITETURAL: A partir de agora, o Kanban exibe cards por GRUPO ECONÔMICO,
 * não por pedidos individuais. Cada card consolida múltiplos pedidos/análises do mesmo grupo.
 */
@Controller
@RequestMapping("/analise")
public class KanbanController {

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private AlertaService alertaService;

    @Autowired
    private WorkflowService workflowService;

    /**
     * GET /analise/kanban - Exibe o dashboard Kanban.
     *
     * NOVA IMPLEMENTAÇÃO: Agrupa análises por GRUPO ECONÔMICO ao invés de pedidos individuais.
     * Cada card do Kanban representa um grupo com múltiplos pedidos consolidados.
     *
     * @param filtro Filtro de workflow: PRAZO (BASE_PRAZO), NOVO (CLIENTE_NOVO), TODOS (padrão)
     * @param session Sessão HTTP contendo o perfil do usuário (FINANCEIRO ou COMERCIAL)
     * @param model Model do Spring para passar dados ao template
     * @return Nome do template Thymeleaf
     */
    @Transactional(readOnly = true)
    @GetMapping("/kanban")
    public String kanban(@RequestParam(required = false, defaultValue = "TODOS") String filtro,
                        HttpSession session,
                        Model model) {

        String perfil = (String) session.getAttribute("perfil");
        if (perfil == null) {
            perfil = "FINANCEIRO"; // Fallback
        }

        // Buscar todas as análises com fetch join (evita LazyInitializationException)
        List<Analise> analises = analiseRepository.findAllWithPedidoAndCliente();

        // Aplicar filtro se necessário
        if ("PRAZO".equals(filtro)) {
            analises = analises.stream()
                .filter(a -> TipoWorkflow.BASE_PRAZO.equals(a.getPedido().getWorkflow()))
                .collect(Collectors.toList());
        } else if ("NOVO".equals(filtro)) {
            analises = analises.stream()
                .filter(a -> TipoWorkflow.CLIENTE_NOVO.equals(a.getPedido().getWorkflow()))
                .collect(Collectors.toList());
        }

        // Calcular alertas para cada pedido (necessário antes do agrupamento)
        for (Analise analise : analises) {
            Pedido pedido = analise.getPedido();
            List<String> alerts = alertaService.calcularAlertas(pedido);
            pedido.setAlerts(alerts);
        }

        // PASSO 1: Agrupar análises por GrupoEconomico
        Map<Long, List<Analise>> analisesPorGrupo = analises.stream()
            .collect(Collectors.groupingBy(a -> a.getPedido().getCliente().getGrupoEconomico().getId()));

        // PASSO 2: Criar GrupoKanbanDTO para cada grupo
        List<GrupoKanbanDTO> gruposKanban = new ArrayList<>();

        for (Map.Entry<Long, List<Analise>> entry : analisesPorGrupo.entrySet()) {
            Long grupoId = entry.getKey();
            List<Analise> analisesDoGrupo = entry.getValue();

            // Buscar GrupoEconomico
            GrupoEconomico grupo = grupoEconomicoRepository.findById(grupoId)
                .orElseThrow(() -> new IllegalStateException("Grupo não encontrado: " + grupoId));

            // Coletar dados consolidados
            List<String> clientesRazaoSocial = analisesDoGrupo.stream()
                .map(a -> a.getPedido().getCliente().getRazaoSocial())
                .distinct()
                .collect(Collectors.toList());

            List<Long> analiseIds = analisesDoGrupo.stream()
                .map(Analise::getId)
                .collect(Collectors.toList());

            BigDecimal valorTotal = analisesDoGrupo.stream()
                .map(a -> a.getPedido().getValor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Pior score (menor) entre os clientes do grupo
            Integer piorScore = analisesDoGrupo.stream()
                .map(a -> a.getPedido().getCliente().getScoreBoaVista())
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

            // Alertas consolidados (união de todos os alertas)
            List<String> alertasConsolidados = analisesDoGrupo.stream()
                .flatMap(a -> a.getPedido().getAlerts().stream())
                .distinct()
                .collect(Collectors.toList());

            // Status e workflow do pedido mais antigo (primeira análise criada)
            Analise analisePrincipal = analisesDoGrupo.stream()
                .min(Comparator.comparing(Analise::getDataInicio))
                .orElse(analisesDoGrupo.get(0));

            StatusWorkflow status = analisePrincipal.getStatusWorkflow();
            TipoWorkflow workflow = analisePrincipal.getPedido().getWorkflow();

            // Construir DTO
            GrupoKanbanDTO grupoDTO = GrupoKanbanDTO.builder()
                .grupoEconomicoId(grupoId)
                .grupoNome(grupo.getNome())
                .grupoCodigo(grupo.getCodigo())
                .clientesRazaoSocial(clientesRazaoSocial)
                .analiseIds(analiseIds)
                .quantidadePedidos(analisesDoGrupo.size())
                .valorTotal(valorTotal)
                .piorScore(piorScore)
                .alertasConsolidados(alertasConsolidados)
                .status(status)
                .workflow(workflow)
                .analisePrincipalId(analisePrincipal.getId())
                .build();

            gruposKanban.add(grupoDTO);
        }

        // PASSO 3: Agrupar GrupoKanbanDTO por workflow e status
        Map<TipoWorkflow, Map<StatusWorkflow, List<GrupoKanbanDTO>>> kanbanData = new EnumMap<>(TipoWorkflow.class);

        // Inicializar estrutura para BASE_PRAZO
        Map<StatusWorkflow, List<GrupoKanbanDTO>> basePrazoMap = new EnumMap<>(StatusWorkflow.class);
        kanbanData.put(TipoWorkflow.BASE_PRAZO, basePrazoMap);

        // Inicializar estrutura para CLIENTE_NOVO
        Map<StatusWorkflow, List<GrupoKanbanDTO>> clienteNovoMap = new EnumMap<>(StatusWorkflow.class);
        kanbanData.put(TipoWorkflow.CLIENTE_NOVO, clienteNovoMap);

        // Agrupar os DTOs
        for (GrupoKanbanDTO grupoDTO : gruposKanban) {
            TipoWorkflow workflow = grupoDTO.getWorkflow();
            StatusWorkflow status = grupoDTO.getStatus();

            Map<StatusWorkflow, List<GrupoKanbanDTO>> workflowMap = kanbanData.get(workflow);
            workflowMap.computeIfAbsent(status, k -> new ArrayList<>()).add(grupoDTO);
        }

        // Passar dados ao template
        model.addAttribute("kanbanData", kanbanData);
        model.addAttribute("filtro", filtro);
        model.addAttribute("perfil", perfil);
        model.addAttribute("statusWorkflowValues", StatusWorkflow.values());
        model.addAttribute("tipoWorkflowValues", TipoWorkflow.values());

        // Passar enums individuais para evitar uso de T() no template
        model.addAttribute("BASE_PRAZO", TipoWorkflow.BASE_PRAZO);
        model.addAttribute("CLIENTE_NOVO", TipoWorkflow.CLIENTE_NOVO);

        model.addAttribute("PENDENTE", StatusWorkflow.PENDENTE);
        model.addAttribute("EM_ANALISE_FINANCEIRO", StatusWorkflow.EM_ANALISE_FINANCEIRO);
        model.addAttribute("DOCUMENTACAO_SOLICITADA", StatusWorkflow.DOCUMENTACAO_SOLICITADA);
        model.addAttribute("DOCUMENTACAO_ENVIADA", StatusWorkflow.DOCUMENTACAO_ENVIADA);
        model.addAttribute("PARECER_APROVADO", StatusWorkflow.PARECER_APROVADO);
        model.addAttribute("PARECER_REPROVADO", StatusWorkflow.PARECER_REPROVADO);
        model.addAttribute("AGUARDANDO_APROVACAO_GESTOR", StatusWorkflow.AGUARDANDO_APROVACAO_GESTOR);
        model.addAttribute("REANALISE_COMERCIAL_SOLICITADA", StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA);
        model.addAttribute("REANALISADO_APROVADO", StatusWorkflow.REANALISADO_APROVADO);
        model.addAttribute("REANALISADO_REPROVADO", StatusWorkflow.REANALISADO_REPROVADO);
        model.addAttribute("FINALIZADO", StatusWorkflow.FINALIZADO);

        return "kanban";
    }

    /**
     * POST /analise/{id}/status - Atualiza o status de uma análise via HTMX.
     *
     * Endpoint usado para drag-and-drop de cards entre colunas.
     *
     * @param id ID da análise
     * @param novoStatus Novo status desejado
     * @param session Sessão HTTP contendo o perfil/analista
     * @return JSON com resultado da operação
     */
    @PostMapping("/{id}/status")
    @ResponseBody
    public Map<String, Object> atualizarStatus(@PathVariable Long id,
                                               @RequestParam StatusWorkflow novoStatus,
                                               HttpSession session) {
        String perfil = (String) session.getAttribute("perfil");
        if (perfil == null) {
            perfil = "SISTEMA";
        }

        try {
            Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));

            // Tentar transicionar usando o WorkflowService
            workflowService.transicionar(analise, novoStatus, perfil);

            return Map.of(
                "success", true,
                "message", "Status atualizado com sucesso"
            );

        } catch (IllegalStateException e) {
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        } catch (Exception e) {
            return Map.of(
                "success", false,
                "error", "Erro ao atualizar status: " + e.getMessage()
            );
        }
    }
}
