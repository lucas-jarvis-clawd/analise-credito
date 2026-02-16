package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade Cliente.
 * Inclui queries customizadas para buscar por CNPJ e grupo econômico.
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busca cliente pelo CNPJ.
     * @param cnpj CNPJ do cliente
     * @return Optional contendo o cliente, se encontrado
     */
    Optional<Cliente> findByCnpj(String cnpj);

    /**
     * Busca todos os clientes de um grupo econômico.
     * @param grupoEconomicoId ID do grupo econômico
     * @return Lista de clientes do grupo
     */
    List<Cliente> findByGrupoEconomicoId(Long grupoEconomicoId);
}
