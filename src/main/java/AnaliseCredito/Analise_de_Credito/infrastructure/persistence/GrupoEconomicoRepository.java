package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório JPA para a entidade GrupoEconomico.
 * Spring Data JPA auto-implementa: save, findById, findAll, delete, etc.
 */
@Repository
public interface GrupoEconomicoRepository extends JpaRepository<GrupoEconomico, Long> {

    /**
     * Busca grupo econômico pelo código único.
     * @param codigo Código do grupo econômico
     * @return Optional contendo o grupo, se encontrado
     */
    Optional<GrupoEconomico> findByCodigo(String codigo);
}
