package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Duplicata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório JPA para a entidade Duplicata.
 * Inclui queries customizadas para buscar por cliente e duplicatas vencidas não pagas.
 */
@Repository
public interface DuplicataRepository extends JpaRepository<Duplicata, Long> {

    /**
     * Busca todas as duplicatas de um cliente.
     * @param clienteId ID do cliente
     * @return Lista de duplicatas do cliente
     */
    List<Duplicata> findByClienteId(Long clienteId);

    /**
     * Busca duplicatas vencidas antes de uma data que ainda não foram pagas.
     * @param date Data limite de vencimento
     * @return Lista de duplicatas vencidas e não pagas
     */
    List<Duplicata> findByVencimentoBeforeAndDataPagamentoIsNull(LocalDate date);
}
