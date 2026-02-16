package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.DadosBI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para a entidade DadosBI.
 * Inclui query customizada para buscar dados ordenados por coleção (usado pelo ScoringService).
 */
@Repository
public interface DadosBIRepository extends JpaRepository<DadosBI, Long> {

    /**
     * Busca dados de BI de um grupo econômico ordenados pela coleção em ordem decrescente.
     * Usado pelo ScoringService para obter as 2 coleções mais recentes.
     * @param grupoEconomicoId ID do grupo econômico
     * @return Lista de dados de BI ordenados por coleção (mais recente primeiro)
     */
    List<DadosBI> findByGrupoEconomicoIdOrderByColecaoDesc(Long grupoEconomicoId);
}
