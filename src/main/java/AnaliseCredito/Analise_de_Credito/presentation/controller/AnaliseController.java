package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.ParecerService;
import AnaliseCredito.Analise_de_Credito.application.service.ScoringService;
import AnaliseCredito.Analise_de_Credito.application.service.WorkflowService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import AnaliseCredito.Analise_de_Credito.presentation.dto.AnaliseForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AnaliseController - Controlador do wizard de análise de crédito.
 *
 * Responsabilidades:
 * 1. Exibir wizard com 6 tabs (Cadastrais, Vínculos, Restrições, Financeiro, Documentos, Histórico)
 * 2. Exibir painel de decisão lateral com score, limites e parecer
 * 3. Processar conclusão da análise (aprovação/reprovação/limitação)
 * 4. Atualizar limite do grupo econômico
 * 5. Gerar parecer CRM para workflow CLIENTE_NOVO
 *
 * IMPORTANTE: Este é o core da análise - onde as decisões de crédito são tomadas.
 */
@Controller
@RequestMapping("/analise")
public class AnaliseController {

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private ParticipacaoRepository participacaoRepository;

    @Autowired
    private PefinRepository pefinRepository;

    @Autowired
    private ProtestoRepository protestoRepository;

    @Autowired
    private AcaoJudicialRepository acaoJudicialRepository;

    @Autowired
    private ChequeRepository chequeRepository;

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private DuplicataRepository duplicataRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ParecerService parecerService;

    /**
     * GET /analise/{id} - Exibe o wizard de análise.
     *
     * Carrega todos os dados necessários para a análise e calcula o limite sugerido
     * se ainda não foi calculado.
     *
     * @param id ID da análise
     * @param model Model do Spring
     * @return Nome do template Thymeleaf
     */
    @GetMapping("/{id}")
    public String exibir(@PathVariable Long id, Model model) {
        // Load análise
        Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));

        Pedido pedido = analise.getPedido();
        Cliente cliente = clienteRepository.findById(analise.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));

        GrupoEconomico grupo = grupoEconomicoRepository.findById(analise.getGrupoEconomicoId())
                .orElseThrow(() -> new IllegalArgumentException("Grupo econômico não encontrado"));

        // Calculate limite sugerido if not set
        if (analise.getLimiteSugerido() == null) {
            BigDecimal limiteSugerido = scoringService.calcularLimiteSugerido(grupo);
            analise.setLimiteSugerido(limiteSugerido);
            analiseRepository.save(analise);
        }

        // Save score snapshot if not set
        if (analise.getScoreNoMomento() == null && cliente.getScoreBoaVista() != null) {
            analise.setScoreNoMomento(cliente.getScoreBoaVista());
            analiseRepository.save(analise);
        }

        // Load related data for wizard tabs
        List<Socio> socios = socioRepository.findByClienteId(cliente.getId());
        List<Participacao> participacoes = participacaoRepository.findByClienteId(cliente.getId());

        // Load restrictions
        List<Pefin> pefins = pefinRepository.findByClienteId(cliente.getId());
        List<Protesto> protestos = protestoRepository.findByClienteId(cliente.getId());
        List<AcaoJudicial> acoesJudiciais = acaoJudicialRepository.findByClienteId(cliente.getId());
        List<Cheque> cheques = chequeRepository.findByClienteId(cliente.getId());

        // Load financial data
        List<DadosBI> dadosBI = dadosBIRepository.findByGrupoEconomicoId(grupo.getId());
        List<Duplicata> duplicatas = duplicataRepository.findByClienteId(cliente.getId());

        // Load documents
        List<Documento> documentos = documentoRepository.findByClienteId(cliente.getId());

        // Generate parecer preview for CLIENTE_NOVO
        String parecerPreview = null;
        if (pedido.getWorkflow() == TipoWorkflow.CLIENTE_NOVO) {
            // Create a temporary analise with decision for preview
            Analise tempAnalise = new Analise();
            tempAnalise.setPedido(pedido);
            tempAnalise.setClienteId(cliente.getId());
            tempAnalise.setDecisao("EM ANÁLISE");
            tempAnalise.setLimiteSugerido(analise.getLimiteSugerido());
            tempAnalise.setDataFim(LocalDateTime.now());
            parecerPreview = parecerService.gerarParecerCRM(tempAnalise);
        }

        // Add all data to model
        model.addAttribute("analise", analise);
        model.addAttribute("pedido", pedido);
        model.addAttribute("cliente", cliente);
        model.addAttribute("grupo", grupo);
        model.addAttribute("socios", socios);
        model.addAttribute("participacoes", participacoes);
        model.addAttribute("pefins", pefins);
        model.addAttribute("protestos", protestos);
        model.addAttribute("acoesJudiciais", acoesJudiciais);
        model.addAttribute("cheques", cheques);
        model.addAttribute("dadosBI", dadosBI);
        model.addAttribute("duplicatas", duplicatas);
        model.addAttribute("documentos", documentos);
        model.addAttribute("parecerPreview", parecerPreview);

        // Calculate totals for display
        BigDecimal totalPefin = pefins.stream()
                .map(Pefin::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProtesto = protestos.stream()
                .map(Protesto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCheque = cheques.stream()
                .map(Cheque::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalPefin", totalPefin);
        model.addAttribute("totalProtesto", totalProtesto);
        model.addAttribute("totalCheque", totalCheque);
        model.addAttribute("totalRestricoes", totalPefin.add(totalProtesto).add(totalCheque));

        return "analise";
    }

    /**
     * POST /analise/{id}/concluir - Conclui a análise e registra a decisão.
     *
     * Processa a decisão do analista, atualiza o limite do grupo econômico,
     * gera parecer CRM (se aplicável) e transiciona o workflow.
     *
     * @param id ID da análise
     * @param form Formulário com decisão, limite e justificativa
     * @param session Sessão HTTP contendo perfil do usuário
     * @param redirectAttributes Atributos para mensagem flash
     * @return Redirect para kanban
     */
    @PostMapping("/{id}/concluir")
    public String concluir(@PathVariable Long id,
                          @ModelAttribute AnaliseForm form,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));

            String perfil = (String) session.getAttribute("perfil");
            if (perfil == null) {
                perfil = "SISTEMA";
            }

            // Validate form
            if (form.getDecisao() == null || form.getDecisao().isEmpty()) {
                redirectAttributes.addFlashAttribute("erro", "Decisão é obrigatória");
                return "redirect:/analise/" + id;
            }

            if ("LIMITADO".equals(form.getDecisao()) &&
                (form.getLimiteAprovado() == null || form.getLimiteAprovado().compareTo(BigDecimal.ZERO) <= 0)) {
                redirectAttributes.addFlashAttribute("erro", "Limite aprovado é obrigatório quando decisão é LIMITADO");
                return "redirect:/analise/" + id;
            }

            if (form.getJustificativa() == null || form.getJustificativa().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("erro", "Justificativa é obrigatória");
                return "redirect:/analise/" + id;
            }

            // Update analise with decision
            analise.setDecisao(form.getDecisao());
            analise.setJustificativa(form.getJustificativa());
            analise.setDataFim(LocalDateTime.now());
            analise.setAnalistaResponsavel(perfil);

            // Set limite aprovado based on decision
            BigDecimal limiteAprovado = calcularLimiteAprovado(form, analise);
            analise.setLimiteAprovado(limiteAprovado);

            // Generate parecer for CLIENTE_NOVO workflow
            if (analise.getPedido().getWorkflow() == TipoWorkflow.CLIENTE_NOVO) {
                String parecer = parecerService.gerarParecerCRM(analise);
                analise.setParecerCRM(parecer);
            }

            // Update grupo limite if APROVADO or LIMITADO
            if ("APROVADO".equals(form.getDecisao()) || "LIMITADO".equals(form.getDecisao())) {
                GrupoEconomico grupo = grupoEconomicoRepository.findById(analise.getGrupoEconomicoId())
                        .orElseThrow();
                grupo.setLimiteAprovado(limiteAprovado);
                // Note: limiteDisponivel should be calculated dynamically based on open orders
                grupoEconomicoRepository.save(grupo);
            }

            // Save analise
            analiseRepository.save(analise);

            // Transition to appropriate final status
            StatusWorkflow novoStatus = determinarStatusFinal(form.getDecisao());
            workflowService.transicionar(analise, novoStatus, perfil);

            redirectAttributes.addFlashAttribute("mensagem",
                    "Análise concluída com sucesso! Decisão: " + form.getDecisao());
            return "redirect:/analise/kanban";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Erro ao concluir análise: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    /**
     * Calcula o limite aprovado com base na decisão.
     *
     * @param form Formulário com decisão
     * @param analise Análise sendo processada
     * @return Limite aprovado calculado
     */
    private BigDecimal calcularLimiteAprovado(AnaliseForm form, Analise analise) {
        return switch (form.getDecisao()) {
            case "APROVADO" -> analise.getLimiteSugerido() != null ?
                    analise.getLimiteSugerido() : BigDecimal.ZERO;
            case "LIMITADO" -> form.getLimiteAprovado() != null ?
                    form.getLimiteAprovado() : BigDecimal.ZERO;
            case "REPROVADO" -> BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Determina o status final do workflow com base na decisão.
     *
     * @param decisao Decisão tomada pelo analista
     * @return Status final correspondente
     */
    private StatusWorkflow determinarStatusFinal(String decisao) {
        return switch (decisao) {
            case "APROVADO", "LIMITADO" -> StatusWorkflow.PARECER_APROVADO;
            case "REPROVADO" -> StatusWorkflow.PARECER_REPROVADO;
            default -> StatusWorkflow.FINALIZADO;
        };
    }
}
