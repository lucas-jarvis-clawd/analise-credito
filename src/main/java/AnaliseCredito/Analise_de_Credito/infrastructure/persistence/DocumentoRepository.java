package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoDocumento;
import AnaliseCredito.Analise_de_Credito.domain.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Documento.
 * Inclui queries customizadas para buscar por cliente e tipo de documento.
 */
@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /**
     * Busca todos os documentos de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de documentos do cliente
     */
    List<Documento> findByClienteId(Long clienteId);

    /**
     * Busca documentos de um cliente filtrados por tipo.
     * @param clienteId ID do cliente
     * @param tipo Tipo de documento
     * @return Lista de documentos do cliente com o tipo especificado
     */
    List<Documento> findByClienteIdAndTipo(Long clienteId, TipoDocumento tipo);
}
