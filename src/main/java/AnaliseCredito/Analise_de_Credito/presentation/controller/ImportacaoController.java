package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.ImportacaoService;
import AnaliseCredito.Analise_de_Credito.presentation.dto.ResultadoImportacao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ImportacaoController - Controller para importação em massa via XLSX.
 *
 * Fornece interface web para upload e processamento de 4 arquivos XLSX:
 * 1. Clientes.xlsx
 * 2. Pedidos.xlsx
 * 3. DadosBI.xlsx
 * 4. Duplicatas.xlsx
 *
 * ROTAS:
 * GET  /importacao           - Exibe formulário de upload
 * POST /importacao/processar - Processa os 4 arquivos
 *
 * VALIDAÇÕES:
 * - Todos os 4 arquivos são obrigatórios
 * - Formato deve ser XLSX (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
 * - Validação de tipo MIME e extensão
 *
 * RESULTADO:
 * - Sucesso: Exibe página de resultado com estatísticas
 * - Erro: Redireciona para formulário com mensagem de erro
 */
@Controller
@RequestMapping("/importacao")
public class ImportacaoController {

    @Autowired
    private ImportacaoService importacaoService;

    /**
     * GET /importacao
     * Exibe formulário de upload de arquivos XLSX.
     */
    @GetMapping
    public String exibirFormulario() {
        return "importacao";
    }

    /**
     * POST /importacao/processar
     * Processa os 4 arquivos XLSX e importa dados para o banco.
     *
     * @param clientes Arquivo Clientes.xlsx
     * @param pedidos Arquivo Pedidos.xlsx
     * @param dadosBI Arquivo DadosBI.xlsx
     * @param duplicatas Arquivo Duplicatas.xlsx
     * @param model Model para resultado
     * @param redirectAttributes Atributos para redirect
     * @return View de resultado ou redirect para formulário em caso de erro
     */
    @PostMapping("/processar")
    public String processar(
            @RequestParam("clientes") MultipartFile clientes,
            @RequestParam("pedidos") MultipartFile pedidos,
            @RequestParam("dadosBI") MultipartFile dadosBI,
            @RequestParam("duplicatas") MultipartFile duplicatas,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // 1. Validate files present
            if (clientes.isEmpty() || pedidos.isEmpty() ||
                dadosBI.isEmpty() || duplicatas.isEmpty()) {
                redirectAttributes.addFlashAttribute("erro",
                    "Todos os 4 arquivos são obrigatórios");
                return "redirect:/importacao";
            }

            // 2. Validate file types (XLSX)
            if (!isXlsxFile(clientes) || !isXlsxFile(pedidos) ||
                !isXlsxFile(dadosBI) || !isXlsxFile(duplicatas)) {
                redirectAttributes.addFlashAttribute("erro",
                    "Todos os arquivos devem ser XLSX");
                return "redirect:/importacao";
            }

            // 3. Process import
            ResultadoImportacao resultado = importacaoService.processar(
                clientes, pedidos, dadosBI, duplicatas
            );

            // 4. Add to model for display
            model.addAttribute("resultado", resultado);

            return "importacao-resultado";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                "Erro ao processar importação: " + e.getMessage());
            return "redirect:/importacao";
        }
    }

    /**
     * Valida se o arquivo é XLSX.
     * Verifica tanto o content type quanto a extensão do arquivo.
     *
     * @param file Arquivo para validar
     * @return true se for XLSX válido
     */
    private boolean isXlsxFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return (contentType != null &&
                contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) ||
               (filename != null && filename.endsWith(".xlsx"));
    }
}
