package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoAnalista;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoDocumento;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Documento;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.DocumentoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.storage.FileStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller para gerenciamento de documentos anexados às análises.
 * Permite upload, listagem e visualização de documentos.
 */
@Controller
public class DocumentoController {

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Faz upload de um documento e cria registro no banco.
     *
     * POST /analise/{analiseId}/documento/upload
     *
     * @param analiseId ID da análise (usado para redirecionamento)
     * @param file Arquivo enviado
     * @param tipo Tipo do documento (IR_SOCIO, NF, OUTROS)
     * @param clienteId ID do cliente proprietário do documento
     * @param session Sessão HTTP (contém perfil do usuário)
     * @param redirectAttributes Atributos para mensagens flash
     * @return Redirect para a tela de análise
     */
    @PostMapping("/analise/{analiseId}/documento/upload")
    public String upload(@PathVariable Long analiseId,
                         @RequestParam("file") MultipartFile file,
                         @RequestParam("tipo") TipoDocumento tipo,
                         @RequestParam("clienteId") Long clienteId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

        try {
            // 1. Buscar cliente
            Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + clienteId));

            // 2. Armazenar arquivo
            String caminhoArquivo = fileStorageService.store(file, cliente.getCnpj());

            // 3. Criar registro do documento
            Documento documento = new Documento();
            documento.setCliente(cliente);
            documento.setTipo(tipo);
            documento.setNomeArquivo(file.getOriginalFilename());
            documento.setCaminhoArquivo(caminhoArquivo);
            documento.setDataUpload(LocalDateTime.now());

            // 4. Definir quem fez o upload (FINANCEIRO ou COMERCIAL)
            String perfilStr = (String) session.getAttribute("perfil");
            if (perfilStr != null) {
                try {
                    TipoAnalista tipoAnalista = TipoAnalista.valueOf(perfilStr);
                    documento.setUploadPor(tipoAnalista);
                } catch (IllegalArgumentException e) {
                    // Se o perfil não for válido, deixa null
                    documento.setUploadPor(null);
                }
            }

            // 5. Salvar no banco
            documentoRepository.save(documento);

            // 6. Mensagem de sucesso
            redirectAttributes.addFlashAttribute("successMessage",
                    "Documento '" + file.getOriginalFilename() + "' enviado com sucesso!");

        } catch (IllegalArgumentException e) {
            // Erro de validação
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erro ao enviar documento: " + e.getMessage());
        } catch (Exception e) {
            // Erro inesperado
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erro inesperado ao enviar documento: " + e.getMessage());
        }

        return "redirect:/analise/" + analiseId;
    }

    /**
     * Lista todos os documentos de um cliente.
     *
     * GET /analise/{analiseId}/documento/list?clienteId=X
     *
     * @param analiseId ID da análise (não usado, mas mantido na URL por consistência)
     * @param clienteId ID do cliente
     * @return Lista de documentos em formato JSON
     */
    @GetMapping("/analise/{analiseId}/documento/list")
    @ResponseBody
    public List<Documento> list(@PathVariable Long analiseId,
                                @RequestParam Long clienteId) {
        return documentoRepository.findByClienteId(clienteId);
    }

    /**
     * Visualiza um documento inline no navegador.
     *
     * GET /documento/{id}/view
     *
     * @param id ID do documento
     * @return ResponseEntity com o arquivo para visualização inline
     */
    @GetMapping("/documento/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        try {
            // 1. Buscar documento no banco
            Documento documento = documentoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + id));

            // 2. Carregar arquivo do sistema
            Resource resource = fileStorageService.load(documento.getCaminhoArquivo());

            // 3. Determinar content type
            String contentType = null;
            try {
                contentType = Files.probeContentType(Paths.get(documento.getNomeArquivo()));
            } catch (IOException e) {
                // Se não conseguir detectar, usa o padrão baseado na extensão
                if (documento.isPdf()) {
                    contentType = "application/pdf";
                } else if (documento.isImagem()) {
                    contentType = "image/jpeg"; // Default para imagens
                } else {
                    contentType = "application/octet-stream";
                }
            }

            // Se ainda for null, usa octet-stream
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 4. Retornar arquivo para visualização inline
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + documento.getNomeArquivo() + "\"")
                    .body(resource);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exclui um documento (arquivo e registro).
     *
     * DELETE /documento/{id}
     *
     * @param id ID do documento
     * @param redirectAttributes Atributos para mensagens flash
     * @return JSON com status da operação
     */
    @DeleteMapping("/documento/{id}")
    @ResponseBody
    public ResponseEntity<String> delete(@PathVariable Long id) {
        try {
            // 1. Buscar documento
            Documento documento = documentoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + id));

            // 2. Excluir arquivo físico
            fileStorageService.delete(documento.getCaminhoArquivo());

            // 3. Excluir registro do banco
            documentoRepository.delete(documento);

            return ResponseEntity.ok("{\"message\": \"Documento excluído com sucesso\"}");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
