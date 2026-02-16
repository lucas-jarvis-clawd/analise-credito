package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Pefin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Pefin (restrições).
 * Inclui query customizada para buscar restrições por cliente.
 */
@Repository
public interface PefinRepository extends JpaRepository<Pefin, Long> {

    /**
     * Busca todas as restrições Pefin de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de restrições Pefin do cliente
     */
    List<Pefin> findByClienteId(Long clienteId);
}
