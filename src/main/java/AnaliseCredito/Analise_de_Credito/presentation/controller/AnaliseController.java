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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
    private PedidoRepository pedidoRepository;

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

        // Build cross-tab: all pedidos from grupo econômico in same coleção
        List<String> marcas = new ArrayList<>();
        List<Map<String, Object>> crossTabRows = new ArrayList<>();
        Map<String, BigDecimal> totalPorMarca = new LinkedHashMap<>();

        if (pedido.getColecao() != null) {
            List<Pedido> pedidosGrupo = pedidoRepository.findByGrupoEconomicoIdAndColecao(
                    grupo.getId(), pedido.getColecao());

            // Collect distinct marcas (sorted)
            TreeSet<String> marcasSet = new TreeSet<>();
            for (Pedido p : pedidosGrupo) {
                marcasSet.add(p.getMarca() != null ? p.getMarca() : "Sem Marca");
            }
            marcas.addAll(marcasSet);

            // Group pedidos by CNPJ
            Map<String, List<Pedido>> pedidosPorCnpj = new LinkedHashMap<>();
            for (Pedido p : pedidosGrupo) {
                String cnpj = p.getCliente().getCnpj();
                pedidosPorCnpj.computeIfAbsent(cnpj, k -> new ArrayList<>()).add(p);
            }

            // Build rows: one per CNPJ with valor totals per marca + individual pedidos
            for (Map.Entry<String, List<Pedido>> entry : pedidosPorCnpj.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("cnpj", entry.getKey());
                row.put("razaoSocial", entry.getValue().get(0).getCliente().getRazaoSocial());

                Map<String, BigDecimal> valoresPorMarca = new LinkedHashMap<>();
                BigDecimal totalCnpjRow = BigDecimal.ZERO;
                for (String m : marcas) {
                    valoresPorMarca.put(m, BigDecimal.ZERO);
                }
                for (Pedido p : entry.getValue()) {
                    String m = p.getMarca() != null ? p.getMarca() : "Sem Marca";
                    valoresPorMarca.merge(m, p.getValor(), BigDecimal::add);
                    totalCnpjRow = totalCnpjRow.add(p.getValor());
                }
                row.put("valoresPorMarca", valoresPorMarca);
                row.put("total", totalCnpjRow);

                List<Map<String, Object>> pedidosDetalhe = new ArrayList<>();
                for (Pedido p : entry.getValue()) {
                    Map<String, Object> pedidoInfo = new LinkedHashMap<>();
                    pedidoInfo.put("numero", p.getNumero());
                    pedidoInfo.put("valor", p.getValor());
                    pedidoInfo.put("marca", p.getMarca() != null ? p.getMarca() : "Sem Marca");
                    pedidoInfo.put("data", p.getData());
                    pedidoInfo.put("bloqueio", p.getBloqueio());
                    pedidoInfo.put("condicaoPagamento", p.getCondicaoPagamento());
                    pedidosDetalhe.add(pedidoInfo);
                }
                row.put("pedidosDetalhe", pedidosDetalhe);
                row.put("pedidosCount", entry.getValue().size());

                crossTabRows.add(row);

                for (String m : marcas) {
                    totalPorMarca.merge(m, valoresPorMarca.get(m), BigDecimal::add);
                }
            }
        }

        model.addAttribute("marcas", marcas);
        model.addAttribute("crossTabRows", crossTabRows);
        model.addAttribute("totalPorMarca", totalPorMarca);
        BigDecimal grandTotal = totalPorMarca.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("grandTotal", grandTotal);

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

    // ==================== CRUD Restricoes ====================

    @PostMapping("/{id}/pefin")
    public String adicionarPefin(@PathVariable Long id,
                                 @RequestParam String origem,
                                 @RequestParam BigDecimal valor,
                                 @RequestParam(required = false) String dataOcorrencia,
                                 RedirectAttributes redirectAttributes) {
        Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
        Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();

        Pefin pefin = new Pefin();
        pefin.setCliente(cliente);
        pefin.setOrigem(origem);
        pefin.setValor(valor);
        if (dataOcorrencia != null && !dataOcorrencia.isBlank()) {
            pefin.setDataOcorrencia(LocalDate.parse(dataOcorrencia));
        }
        pefinRepository.save(pefin);

        redirectAttributes.addFlashAttribute("mensagem", "PEFIN adicionado com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/pefin/{pefinId}/excluir")
    public String excluirPefin(@PathVariable Long id,
                               @PathVariable Long pefinId,
                               RedirectAttributes redirectAttributes) {
        pefinRepository.deleteById(pefinId);
        redirectAttributes.addFlashAttribute("mensagem", "PEFIN removido com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/protesto")
    public String adicionarProtesto(@PathVariable Long id,
                                    @RequestParam String cartorio,
                                    @RequestParam BigDecimal valor,
                                    @RequestParam(required = false) String dataProtesto,
                                    RedirectAttributes redirectAttributes) {
        Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
        Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();

        Protesto protesto = new Protesto();
        protesto.setCliente(cliente);
        protesto.setCartorio(cartorio);
        protesto.setValor(valor);
        if (dataProtesto != null && !dataProtesto.isBlank()) {
            protesto.setDataProtesto(LocalDate.parse(dataProtesto));
        }
        protestoRepository.save(protesto);

        redirectAttributes.addFlashAttribute("mensagem", "Protesto adicionado com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/protesto/{protestoId}/excluir")
    public String excluirProtesto(@PathVariable Long id,
                                  @PathVariable Long protestoId,
                                  RedirectAttributes redirectAttributes) {
        protestoRepository.deleteById(protestoId);
        redirectAttributes.addFlashAttribute("mensagem", "Protesto removido com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/acao-judicial")
    public String adicionarAcaoJudicial(@PathVariable Long id,
                                        @RequestParam String tipo,
                                        @RequestParam(required = false) String vara,
                                        @RequestParam(required = false) BigDecimal valor,
                                        @RequestParam(required = false) String dataDistribuicao,
                                        RedirectAttributes redirectAttributes) {
        Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
        Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();

        AcaoJudicial acao = new AcaoJudicial();
        acao.setCliente(cliente);
        acao.setTipo(tipo);
        acao.setVara(vara);
        if (valor != null) {
            acao.setValor(valor);
        }
        if (dataDistribuicao != null && !dataDistribuicao.isBlank()) {
            acao.setDataDistribuicao(LocalDate.parse(dataDistribuicao));
        }
        acaoJudicialRepository.save(acao);

        redirectAttributes.addFlashAttribute("mensagem", "Ação Judicial adicionada com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/acao-judicial/{acaoId}/excluir")
    public String excluirAcaoJudicial(@PathVariable Long id,
                                      @PathVariable Long acaoId,
                                      RedirectAttributes redirectAttributes) {
        acaoJudicialRepository.deleteById(acaoId);
        redirectAttributes.addFlashAttribute("mensagem", "Ação Judicial removida com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/cheque")
    public String adicionarCheque(@PathVariable Long id,
                                  @RequestParam String banco,
                                  @RequestParam(required = false) String agencia,
                                  @RequestParam BigDecimal valor,
                                  @RequestParam(required = false) String dataOcorrencia,
                                  RedirectAttributes redirectAttributes) {
        Analise analise = analiseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
        Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();

        Cheque cheque = new Cheque();
        cheque.setCliente(cliente);
        cheque.setBanco(banco);
        cheque.setAgencia(agencia);
        cheque.setValor(valor);
        if (dataOcorrencia != null && !dataOcorrencia.isBlank()) {
            cheque.setDataOcorrencia(LocalDate.parse(dataOcorrencia));
        }
        chequeRepository.save(cheque);

        redirectAttributes.addFlashAttribute("mensagem", "Cheque adicionado com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
    }

    @PostMapping("/{id}/cheque/{chequeId}/excluir")
    public String excluirCheque(@PathVariable Long id,
                                @PathVariable Long chequeId,
                                RedirectAttributes redirectAttributes) {
        chequeRepository.deleteById(chequeId);
        redirectAttributes.addFlashAttribute("mensagem", "Cheque removido com sucesso.");
        return "redirect:/analise/" + id + "#restricoes";
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
