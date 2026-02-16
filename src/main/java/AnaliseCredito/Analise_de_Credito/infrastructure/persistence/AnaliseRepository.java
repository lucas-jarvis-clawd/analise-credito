package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade Analise.
 * Inclui queries customizadas para buscar por pedido, status e cliente.
 */
@Repository
public interface AnaliseRepository extends JpaRepository<Analise, Long> {

    /**
     * Busca análise associada a um pedido específico.
     * @param pedidoId ID do pedido
     * @return Optional contendo a análise, se encontrada
     */
    Optional<Analise> findByPedidoId(Long pedidoId);

    /**
     * Busca todas as análises com um status específico.
     * @param status Status do workflow
     * @return Lista de análises com o status especificado
     */
    List<Analise> findByStatusWorkflow(StatusWorkflow status);

    /**
     * Busca todas as análises de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de análises do cliente
     */
    List<Analise> findByClienteId(Long clienteId);
}
