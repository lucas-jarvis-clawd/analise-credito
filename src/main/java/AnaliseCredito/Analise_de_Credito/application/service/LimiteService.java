package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.HistoricoLimiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Serviço responsável por atualizar o limite aprovado de um GrupoEconomico.
 *
 * Operação atômica: insere HistoricoLimite + atualiza GrupoEconomico.limiteAprovado
 * na mesma transação.
 */
@Service
public class LimiteService {

    @Autowired
    private GrupoEconomicoRepository grupoRepository;

    @Autowired
    private HistoricoLimiteRepository historicoRepository;

    /**
     * Atualiza o limite aprovado de um grupo, gravando o histórico.
     *
     * @param grupoId     ID do GrupoEconomico
     * @param valor       Novo valor do limite
     * @param responsavel Nome do analista que registrou a alteração
     * @throws EntityNotFoundException se o grupo não for encontrado
     */
    @Transactional
    public void atualizarLimite(Long grupoId, BigDecimal valor, String responsavel) {
        GrupoEconomico grupo = grupoRepository.findById(grupoId)
            .orElseThrow(() -> new EntityNotFoundException(
                "GrupoEconomico não encontrado: id=" + grupoId));

        HistoricoLimite historico = new HistoricoLimite();
        historico.setGrupoEconomico(grupo);
        historico.setValor(valor);
        historico.setResponsavel(responsavel);
        historicoRepository.save(historico);

        grupo.setLimiteAprovado(valor);
        grupoRepository.save(grupo);
    }
}
