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
     * Busca todos os pedidos de uma coleção para todos os clientes de um grupo econômico.
     * @param grupoEconomicoId ID do grupo econômico
     * @param colecao Coleção no formato AAAAMM
     * @return Lista de pedidos do grupo na coleção
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Pedido p JOIN FETCH p.cliente c " +
        "WHERE c.grupoEconomico.id = :grupoEconomicoId AND p.colecao = :colecao " +
        "ORDER BY c.cnpj, p.marca, p.numero"
    )
    List<Pedido> findByGrupoEconomicoIdAndColecao(
        @org.springframework.data.repository.query.Param("grupoEconomicoId") Long grupoEconomicoId,
        @org.springframework.data.repository.query.Param("colecao") Integer colecao
    );
}
