package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.DadosBI;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.DadosBIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ScoringService - Calcula limites de crédito sugeridos baseados em dados de BI e score.
 *
 * Algoritmo:
 * 1. Busca últimas 2 coleções do BI do grupo
 * 2. Pega maior crédito entre as 2
 * 3. Busca score interno da coleção mais recente
 * 4. Aplica fator multiplicador baseado no score (800+: 1.5, 600+: 1.2, 400+: 1.0, <400: 0.7)
 * 5. Calcula limite = maiorCredito * fator
 * 6. Aplica cap para SIMEI se necessário
 * 7. Retorna limite calculado
 */
@Service
public class ScoringService {

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    /**
     * Calcula o limite de crédito sugerido para um grupo econômico.
     *
     * @param grupo Grupo econômico para calcular o limite
     * @return Limite sugerido calculado
     * @throws RuntimeException se configuração não for encontrada
     */
    public BigDecimal calcularLimiteSugerido(GrupoEconomico grupo) {
        // 1. Buscar últimas 2 coleções BI do grupo
        List<DadosBI> ultimas2 = dadosBIRepository
                .findByGrupoEconomicoIdOrderByColecaoDesc(grupo.getId())
                .stream()
                .limit(2)
                .collect(Collectors.toList());

        if (ultimas2.isEmpty()) {
            return BigDecimal.ZERO; // sem dados, retorna zero
        }

        // 2. Pegar maior crédito entre as 2 coleções
        BigDecimal maiorCredito = ultimas2.stream()
                .map(DadosBI::getCredito)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 3. Score interno da coleção mais recente
        Integer scoreInterno = ultimas2.get(0).getScore();

        // 4. Buscar configuração
        Configuracao config = configuracaoRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Configuração não encontrada"));

        // 5. Fator multiplicador baseado no score
        BigDecimal fator;
        if (scoreInterno >= 800) {
            fator = config.getScoreAltoMultiplicador();
        } else if (scoreInterno >= 600) {
            fator = config.getScoreMedioMultiplicador();
        } else if (scoreInterno >= 400) {
            fator = config.getScoreNormalMultiplicador();
        } else {
            fator = config.getScoreBaixoMultiplicador();
        }

        // 6. Calcular limite
        BigDecimal limite = maiorCredito.multiply(fator);

        // 7. Cap para SIMEI
        boolean temSimeiComPedido = grupo.getClientes().stream()
                .anyMatch(c -> c.getSimei() && !c.getPedidos().isEmpty());

        if (temSimeiComPedido && limite.compareTo(config.getLimiteSimei()) > 0) {
            limite = config.getLimiteSimei();
        }

        return limite;
    }
}
