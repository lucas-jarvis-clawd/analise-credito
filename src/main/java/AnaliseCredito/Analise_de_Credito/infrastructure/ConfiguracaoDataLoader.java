package AnaliseCredito.Analise_de_Credito.infrastructure;

import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * DataLoader para a entidade Configuracao.
 *
 * Executa na inicialização da aplicação e cria o registro SINGLETON (ID=1)
 * com os valores padrão de configuração do sistema.
 *
 * É idempotente: só cria se a tabela estiver vazia.
 */
@Component
public class ConfiguracaoDataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracaoDataLoader.class);

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Override
    public void run(String... args) throws Exception {
        if (configuracaoRepository.count() == 0) {
            logger.info("Nenhuma configuração encontrada. Criando configuração padrão...");

            Configuracao config = new Configuracao();

            // Limites SIMEI
            config.setLimiteSimei(new BigDecimal("35000"));
            config.setMaxSimeisPorGrupo(2);

            // Thresholds de Score
            config.setScoreBaixoThreshold(300);

            // Multiplicadores de Score
            config.setScoreAltoMultiplicador(new BigDecimal("1.5"));
            config.setScoreMedioMultiplicador(new BigDecimal("1.2"));
            config.setScoreNormalMultiplicador(new BigDecimal("1.0"));
            config.setScoreBaixoMultiplicador(new BigDecimal("0.7"));

            // Alçada (Aprovação de Gestor)
            config.setValorAprovacaoGestor(new BigDecimal("100000"));
            config.setTotalGrupoAprovacaoGestor(new BigDecimal("200000"));
            config.setRestricoesAprovacaoGestor(5);

            Configuracao saved = configuracaoRepository.save(config);

            logger.info("Configuração padrão criada com sucesso! ID: {}", saved.getId());
            logger.info("  - Limite SIMEI: {}", saved.getLimiteSimei());
            logger.info("  - Max SIMEIs por grupo: {}", saved.getMaxSimeisPorGrupo());
            logger.info("  - Score baixo threshold: {}", saved.getScoreBaixoThreshold());
            logger.info("  - Multiplicadores: Alto={}, Médio={}, Normal={}, Baixo={}",
                    saved.getScoreAltoMultiplicador(),
                    saved.getScoreMedioMultiplicador(),
                    saved.getScoreNormalMultiplicador(),
                    saved.getScoreBaixoMultiplicador());
            logger.info("  - Aprovação gestor: Valor={}, Total Grupo={}, Restrições={}",
                    saved.getValorAprovacaoGestor(),
                    saved.getTotalGrupoAprovacaoGestor(),
                    saved.getRestricoesAprovacaoGestor());
        } else {
            logger.info("Configuração já existe. Count: {}", configuracaoRepository.count());
        }
    }
}
