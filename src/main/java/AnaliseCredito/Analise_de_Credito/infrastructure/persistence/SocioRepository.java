package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para a entidade Socio.
 * Spring Data JPA auto-implementa: save, findById, findAll, delete, etc.
 */
@Repository
public interface SocioRepository extends JpaRepository<Socio, Long> {
}
