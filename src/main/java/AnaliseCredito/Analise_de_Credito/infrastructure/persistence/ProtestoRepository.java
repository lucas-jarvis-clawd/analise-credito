package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Protesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Protesto (restrições).
 * Inclui query customizada para buscar restrições por cliente.
 */
@Repository
public interface ProtestoRepository extends JpaRepository<Protesto, Long> {

    /**
     * Busca todos os protestos de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de protestos do cliente
     */
    List<Protesto> findByClienteId(Long clienteId);
}
