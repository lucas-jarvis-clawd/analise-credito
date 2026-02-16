package AnaliseCredito.Analise_de_Credito.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Serviço para gerenciamento de arquivos no sistema de arquivos.
 * Responsável por armazenar, carregar e excluir documentos anexados às análises.
 *
 * Estrutura de diretórios: uploads/{cnpj}/
 */
@Service
public class FileStorageService {

    @Value("${upload.path}")
    private String uploadPath;

    private Path rootLocation;

    /**
     * Inicializa o diretório raiz de uploads.
     * Chamado automaticamente após a construção do bean.
     */
    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadPath);
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    /**
     * Armazena um arquivo no sistema de arquivos.
     *
     * @param file Arquivo a ser armazenado
     * @param cnpj CNPJ do cliente (usado para criar subdiretório)
     * @return Caminho relativo do arquivo armazenado (cnpj/filename)
     * @throws IllegalArgumentException se o arquivo for inválido
     * @throws RuntimeException se ocorrer erro ao salvar
     */
    public String store(MultipartFile file, String cnpj) {
        // 1. Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        // 2. Validate content type
        String contentType = file.getContentType();
        if (!isValidContentType(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PDF and images (JPG, PNG) are allowed");
        }

        // 3. Validate file size (already handled by Spring, but double-check)
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB
            throw new IllegalArgumentException("File size exceeds maximum allowed size (10MB)");
        }

        try {
            // 4. Create CNPJ directory
            Path cnpjDir = rootLocation.resolve(cnpj);
            Files.createDirectories(cnpjDir);

            // 5. Generate unique filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.contains("..")) {
                throw new IllegalArgumentException("Invalid filename: " + originalFilename);
            }

            String filename = System.currentTimeMillis() + "_" + originalFilename;

            // 6. Save file
            Path destinationFile = cnpjDir.resolve(filename);

            // Ensure the file is within the upload directory (security check)
            if (!destinationFile.getParent().equals(cnpjDir)) {
                throw new IllegalArgumentException("Cannot store file outside designated directory");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // 7. Return relative path
            return cnpj + "/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * Carrega um arquivo como Resource.
     *
     * @param path Caminho relativo do arquivo (cnpj/filename)
     * @return Resource do arquivo
     * @throws RuntimeException se o arquivo não for encontrado ou não puder ser lido
     */
    public Resource load(String path) {
        try {
            Path file = rootLocation.resolve(path);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + path);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + path, e);
        }
    }

    /**
     * Exclui um arquivo do sistema de arquivos.
     *
     * @param path Caminho relativo do arquivo (cnpj/filename)
     * @throws RuntimeException se ocorrer erro ao excluir
     */
    public void delete(String path) {
        try {
            Path file = rootLocation.resolve(path);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + path, e);
        }
    }

    /**
     * Valida o tipo de conteúdo do arquivo.
     * Apenas PDF e imagens são permitidos.
     *
     * @param contentType Content-Type do arquivo
     * @return true se o tipo for válido, false caso contrário
     */
    private boolean isValidContentType(String contentType) {
        if (contentType == null) {
            return false;
        }

        return contentType.equals("application/pdf") ||
               contentType.equals("image/jpeg") ||
               contentType.equals("image/jpg") ||
               contentType.equals("image/png") ||
               contentType.equals("image/gif");
    }
}
