package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para a entidade Configuracao.
 * Esta tabela deve conter apenas 1 registro (ID=1) com os parâmetros do sistema.
 * Spring Data JPA auto-implementa: save, findById, findAll, delete, etc.
 */
@Repository
public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {
}
