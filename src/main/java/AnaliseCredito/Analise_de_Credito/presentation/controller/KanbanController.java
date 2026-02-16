package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.AlertaService;
import AnaliseCredito.Analise_de_Credito.application.service.WorkflowService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AnaliseRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * KanbanController - Controlador do dashboard Kanban de análises.
 *
 * Responsabilidades:
 * 1. Exibir dashboard com análises agrupadas por workflow e status
 * 2. Filtrar análises por tipo (BASE_PRAZO, CLIENTE_NOVO, TODOS)
 * 3. Calcular alertas para cada pedido
 * 4. Gerenciar drag-and-drop de cards entre colunas via HTMX
 * 5. Atualizar status de análises via transições do workflow
 *
 * IMPORTANTE: Este é o coração da aplicação - onde os analistas visualizam
 * e gerenciam o pipeline de análise de crédito.
 */
@Controller
@RequestMapping("/analise")
public class KanbanController {

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private AlertaService alertaService;

    @Autowired
    private WorkflowService workflowService;

    /**
     * GET /analise/kanban - Exibe o dashboard Kanban.
     *
     * @param filtro Filtro de workflow: PRAZO (BASE_PRAZO), NOVO (CLIENTE_NOVO), TODOS (padrão)
     * @param session Sessão HTTP contendo o perfil do usuário (FINANCEIRO ou COMERCIAL)
     * @param model Model do Spring para passar dados ao template
     * @return Nome do template Thymeleaf
     */
    @GetMapping("/kanban")
    public String kanban(@RequestParam(required = false, defaultValue = "TODOS") String filtro,
                        HttpSession session,
                        Model model) {

        String perfil = (String) session.getAttribute("perfil");
        if (perfil == null) {
            perfil = "FINANCEIRO"; // Fallback
        }

        // Buscar todas as análises (eager fetching de pedido e cliente)
        List<Analise> analises = analiseRepository.findAll();

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

        // Agrupar análises por workflow e status
        Map<TipoWorkflow, Map<StatusWorkflow, List<Analise>>> kanbanData = new EnumMap<>(TipoWorkflow.class);

        // Inicializar estrutura para BASE_PRAZO
        Map<StatusWorkflow, List<Analise>> basePrazoMap = new EnumMap<>(StatusWorkflow.class);
        kanbanData.put(TipoWorkflow.BASE_PRAZO, basePrazoMap);

        // Inicializar estrutura para CLIENTE_NOVO
        Map<StatusWorkflow, List<Analise>> clienteNovoMap = new EnumMap<>(StatusWorkflow.class);
        kanbanData.put(TipoWorkflow.CLIENTE_NOVO, clienteNovoMap);

        // Agrupar as análises
        for (Analise analise : analises) {
            TipoWorkflow workflow = analise.getPedido().getWorkflow();
            StatusWorkflow status = analise.getStatusWorkflow();

            Map<StatusWorkflow, List<Analise>> workflowMap = kanbanData.get(workflow);
            workflowMap.computeIfAbsent(status, k -> new java.util.ArrayList<>()).add(analise);

            // Calcular alertas para cada pedido
            Pedido pedido = analise.getPedido();
            List<String> alerts = alertaService.calcularAlertas(pedido);
            pedido.setAlerts(alerts);
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
