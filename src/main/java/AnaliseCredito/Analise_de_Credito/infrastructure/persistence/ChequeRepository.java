package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Cheque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Cheque (restrições).
 * Inclui query customizada para buscar restrições por cliente.
 */
@Repository
public interface ChequeRepository extends JpaRepository<Cheque, Long> {

    /**
     * Busca todos os cheques sem fundo de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de cheques sem fundo do cliente
     */
    List<Cheque> findByClienteId(Long clienteId);
}
