package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.ClienteNovoValidationService;
import AnaliseCredito.Analise_de_Credito.application.service.WorkflowService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AnaliseRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Controller para o pipeline de validação de CLIENTE_NOVO.
 *
 * Cada endpoint corresponde a uma etapa do pipeline:
 * 1. iniciar-pipeline - roda gates automáticos em cascata
 * 2. consultas-realizadas - analista preencheu dados de consulta
 * 3. confirmar-protestos - analista confirma verificação de protestos
 * 4. confirmar-loja - analista informa data de abertura da loja
 * 5. confirmar-score-restricoes - analista confirma score e restrições
 */
@Controller
@RequestMapping("/analise")
public class ClienteNovoController {

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ClienteNovoValidationService validationService;

    /**
     * Inicia o pipeline de cliente novo rodando gates automáticos em cascata.
     */
    @PostMapping("/{id}/iniciar-pipeline")
    public String iniciarPipeline(@PathVariable Long id,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
            Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();
            String perfil = getPerfil(session);

            // Gate 1: Tem dados de consulta?
            if (!validationService.hasConsultaData(cliente)) {
                workflowService.transicionar(analise, StatusWorkflow.FAZER_CONSULTAS, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Realize as consultas cadastrais para prosseguir.");
                return "redirect:/analise/" + id;
            }

            // Gate 2: Validação cadastral
            String motivoCadastral = validationService.validarCadastral(cliente);
            if (motivoCadastral != null) {
                analise.setMotivoDesvio(motivoCadastral);
                workflowService.transicionar(analise, StatusWorkflow.SOLICITAR_CANCELAMENTO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para cancelamento: " + motivoCadastral);
                return "redirect:/analise/" + id;
            }

            // Gate 3: Fundação recente?
            if (validationService.isFundacaoRecente(cliente)) {
                analise.setMotivoDesvio("Empresa com fundação inferior ao período mínimo");
                workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para antecipado: fundação recente.");
                return "redirect:/analise/" + id;
            }

            // Todos os gates automáticos OK → vai para CONSULTA_PROTESTOS
            workflowService.transicionar(analise, StatusWorkflow.CONSULTA_PROTESTOS, perfil);
            redirectAttributes.addFlashAttribute("mensagem", "Consultas validadas. Verifique os protestos.");
            return "redirect:/analise/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao iniciar pipeline: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    /**
     * Analista preencheu dados de consulta (statusReceita, statusSimples, sintegra, cnae).
     * Roda gates automáticos (cadastral + fundação) em cascata.
     */
    @PostMapping("/{id}/consultas-realizadas")
    public String consultasRealizadas(@PathVariable Long id,
                                       @RequestParam String statusReceita,
                                       @RequestParam(required = false) String statusSimples,
                                       @RequestParam String sintegra,
                                       @RequestParam(required = false) String cnae,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
            Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();
            String perfil = getPerfil(session);

            // Salvar dados no cliente
            cliente.setStatusReceita(statusReceita);
            cliente.setStatusSimples(statusSimples);
            cliente.setSintegra(sintegra);
            cliente.setCnae(cnae);
            clienteRepository.save(cliente);

            // Gate cadastral
            String motivoCadastral = validationService.validarCadastral(cliente);
            if (motivoCadastral != null) {
                analise.setMotivoDesvio(motivoCadastral);
                workflowService.transicionar(analise, StatusWorkflow.SOLICITAR_CANCELAMENTO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para cancelamento: " + motivoCadastral);
                return "redirect:/analise/" + id;
            }

            // Gate fundação
            if (validationService.isFundacaoRecente(cliente)) {
                analise.setMotivoDesvio("Empresa com fundação inferior ao período mínimo");
                workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para antecipado: fundação recente.");
                return "redirect:/analise/" + id;
            }

            // Tudo OK → CONSULTA_PROTESTOS
            workflowService.transicionar(analise, StatusWorkflow.CONSULTA_PROTESTOS, perfil);
            redirectAttributes.addFlashAttribute("mensagem", "Consultas validadas. Verifique os protestos.");
            return "redirect:/analise/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    /**
     * Analista confirma que já verificou protestos (inseridos na aba Restrições).
     * Roda check de protesto acima do threshold.
     */
    @PostMapping("/{id}/confirmar-protestos")
    public String confirmarProtestos(@PathVariable Long id,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
            Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();
            String perfil = getPerfil(session);

            if (validationService.hasProtestoAcima(cliente)) {
                analise.setMotivoDesvio("Protesto com valor acima do limite permitido");
                workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para antecipado: protesto acima do limite.");
                return "redirect:/analise/" + id;
            }

            workflowService.transicionar(analise, StatusWorkflow.VERIFICACAO_LOJA_FISICA, perfil);
            redirectAttributes.addFlashAttribute("mensagem", "Protestos verificados. Informe dados da loja física.");
            return "redirect:/analise/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    /**
     * Analista informa data de abertura da loja física.
     * Roda check de loja recente.
     */
    @PostMapping("/{id}/confirmar-loja")
    public String confirmarLoja(@PathVariable Long id,
                                 @RequestParam String dataAberturaLoja,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
            Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();
            String perfil = getPerfil(session);

            // Salvar data de abertura da loja
            cliente.setDataAberturaLoja(LocalDate.parse(dataAberturaLoja));
            clienteRepository.save(cliente);

            if (validationService.isLojaRecente(cliente)) {
                analise.setMotivoDesvio("Loja física com abertura inferior ao período mínimo");
                workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para antecipado: loja recente.");
                return "redirect:/analise/" + id;
            }

            workflowService.transicionar(analise, StatusWorkflow.CONSULTA_SCORE_RESTRICOES, perfil);
            redirectAttributes.addFlashAttribute("mensagem", "Loja verificada. Verifique score e restrições.");
            return "redirect:/analise/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    /**
     * Analista confirma que já verificou score e restrições.
     * Roda check de restrições acima do threshold.
     */
    @PostMapping("/{id}/confirmar-score-restricoes")
    public String confirmarScoreRestricoes(@PathVariable Long id,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) {
        try {
            Analise analise = analiseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada: " + id));
            Cliente cliente = clienteRepository.findById(analise.getClienteId()).orElseThrow();
            String perfil = getPerfil(session);

            if (validationService.hasRestricaoAcima(cliente)) {
                analise.setMotivoDesvio("Restrições com valor total acima do limite permitido");
                workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, perfil);
                redirectAttributes.addFlashAttribute("mensagem", "Análise encaminhada para antecipado: restrições acima do limite.");
                return "redirect:/analise/" + id;
            }

            workflowService.transicionar(analise, StatusWorkflow.EM_ANALISE_CLIENTE_NOVO, perfil);
            redirectAttributes.addFlashAttribute("mensagem", "Validações concluídas. Defina o limite e parecer.");
            return "redirect:/analise/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/analise/" + id;
        }
    }

    private String getPerfil(HttpSession session) {
        String perfil = (String) session.getAttribute("perfil");
        return perfil != null ? perfil : "SISTEMA";
    }
}
