package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório para HistoricoLimite.
 * Sempre retorna registros em ordem decrescente por data (mais recente primeiro).
 */
@Repository
public interface HistoricoLimiteRepository extends JpaRepository<HistoricoLimite, Long> {

    /**
     * Retorna histórico de limites de um grupo, do mais recente ao mais antigo.
     */
    List<HistoricoLimite> findByGrupoEconomicoIdOrderByDataRegistroDesc(Long grupoId);
}
