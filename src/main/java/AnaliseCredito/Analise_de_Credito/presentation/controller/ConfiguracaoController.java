package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ConfiguracaoController - Gerencia parâmetros globais do sistema.
 *
 * Permite administradores ajustarem regras de negócio em runtime
 * sem necessidade de alterações no código.
 */
@Controller
@RequestMapping("/configuracao")
public class ConfiguracaoController {

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    /**
     * GET /configuracao - Exibe o formulário de configuração
     */
    @GetMapping
    public String exibir(Model model) {
        Configuracao config = configuracaoRepository.findById(1L)
            .orElseThrow(() -> new RuntimeException("Configuração não encontrada"));

        model.addAttribute("config", config);
        return "configuracao";
    }

    /**
     * POST /configuracao/salvar - Atualiza os parâmetros de configuração
     */
    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Configuracao config,
                        RedirectAttributes redirectAttributes) {
        // Ensure ID is always 1 (singleton pattern)
        config.setId(1L);

        configuracaoRepository.save(config);

        redirectAttributes.addFlashAttribute("mensagem",
            "Configuração atualizada com sucesso!");

        return "redirect:/configuracao";
    }
}
