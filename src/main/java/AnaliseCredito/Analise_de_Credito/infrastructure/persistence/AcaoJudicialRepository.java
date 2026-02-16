package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.AcaoJudicial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade AcaoJudicial (restrições).
 * Inclui query customizada para buscar restrições por cliente.
 */
@Repository
public interface AcaoJudicialRepository extends JpaRepository<AcaoJudicial, Long> {

    /**
     * Busca todas as ações judiciais de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de ações judiciais do cliente
     */
    List<AcaoJudicial> findByClienteId(Long clienteId);
}
