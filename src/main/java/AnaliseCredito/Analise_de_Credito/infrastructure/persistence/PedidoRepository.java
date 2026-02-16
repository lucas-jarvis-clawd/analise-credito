package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Pedido.
 * Inclui queries customizadas para buscar por cliente e tipo de workflow.
 */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Busca todos os pedidos de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de pedidos do cliente
     */
    List<Pedido> findByClienteId(Long clienteId);

    /**
     * Busca todos os pedidos de um tipo de workflow específico.
     * @param workflow Tipo de workflow
     * @return Lista de pedidos com o workflow especificado
     */
    List<Pedido> findByWorkflow(TipoWorkflow workflow);

    /**
     * Busca todos os pedidos de clientes pertencentes a um grupo econômico.
     * @param grupoEconomicoId ID do grupo econômico
     * @return Lista de pedidos de todos os clientes do grupo
     */
    List<Pedido> findByClienteGrupoEconomicoId(Long grupoEconomicoId);
}
