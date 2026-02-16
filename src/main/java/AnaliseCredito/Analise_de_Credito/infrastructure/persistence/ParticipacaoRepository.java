package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Participacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade Participacao.
 * Spring Data JPA auto-implementa: save, findById, findAll, delete, etc.
 */
@Repository
public interface ParticipacaoRepository extends JpaRepository<Participacao, Long> {

    /**
     * Busca todas as participações de um cliente em outras empresas.
     * @param clienteId ID do cliente
     * @return Lista de participações do cliente
     */
    List<Participacao> findByClienteId(Long clienteId);
}
