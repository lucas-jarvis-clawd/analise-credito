package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Busca grupos com filtro opcional de UF (via clientes) e busca por nome/código.
     * UF: grupo aparece se pelo menos um cliente está na UF informada.
     * Busca: case-insensitive por nome ou exato por código.
     * Passe null para desativar qualquer filtro.
     * Uses JOIN (not LEFT JOIN) — groups with no clients do not appear.
     */
    @Query("""
        SELECT DISTINCT g FROM GrupoEconomico g
        JOIN g.clientes c
        WHERE (:uf IS NULL OR c.estado = :uf)
          AND (:busca IS NULL
               OR LOWER(g.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
               OR g.codigo LIKE CONCAT('%', :busca, '%'))
        ORDER BY g.nome
        """)
    List<GrupoEconomico> findByFiltros(@Param("uf") String uf, @Param("busca") String busca);
}
