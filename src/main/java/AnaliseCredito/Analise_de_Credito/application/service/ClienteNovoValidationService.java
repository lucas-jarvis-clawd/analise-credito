package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.Pefin;
import AnaliseCredito.Analise_de_Credito.domain.model.Protesto;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PefinRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ProtestoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AcaoJudicialRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ChequeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Serviço de validação para o pipeline de CLIENTE_NOVO.
 *
 * Cada método corresponde a um gate do pipeline:
 * 1. hasConsultaData - verifica se dados de consulta foram preenchidos
 * 2. validarCadastral - verifica receita, sintegra, CNAE
 * 3. isFundacaoRecente - verifica se empresa tem menos de X meses
 * 4. hasProtestoAcima - verifica se algum protesto ultrapassa threshold
 * 5. isLojaRecente - verifica se loja tem menos de X meses
 * 6. hasRestricaoAcima - verifica se total de restrições ultrapassa threshold
 */
@Service
public class ClienteNovoValidationService {

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private ProtestoRepository protestoRepository;

    @Autowired
    private PefinRepository pefinRepository;

    @Autowired
    private AcaoJudicialRepository acaoJudicialRepository;

    @Autowired
    private ChequeRepository chequeRepository;

    private Configuracao getConfig() {
        return configuracaoRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Configuração não encontrada"));
    }

    /**
     * Verifica se o cliente tem dados de consulta preenchidos
     * (statusReceita + sintegra são os mínimos obrigatórios).
     */
    public boolean hasConsultaData(Cliente cliente) {
        return cliente.getStatusReceita() != null && !cliente.getStatusReceita().isBlank()
                && cliente.getSintegra() != null && !cliente.getSintegra().isBlank();
    }

    /**
     * Valida dados cadastrais do cliente.
     * Retorna o motivo de falha ou null se tudo OK.
     *
     * Regras:
     * - Receita Federal: status diferente de "ATIVA" → cancelamento
     * - Sintegra: "INABILITADO" ou "SUSPENSO" → cancelamento
     * - CNAE: não está na lista de permitidos → cancelamento
     */
    public String validarCadastral(Cliente cliente) {
        Configuracao config = getConfig();

        // Verifica status da Receita Federal
        if (cliente.getStatusReceita() != null && !"ATIVA".equalsIgnoreCase(cliente.getStatusReceita())) {
            return "Receita Federal: situação " + cliente.getStatusReceita();
        }

        // Verifica Sintegra
        if (cliente.getSintegra() != null &&
                ("INABILITADO".equalsIgnoreCase(cliente.getSintegra()) ||
                 "SUSPENSO".equalsIgnoreCase(cliente.getSintegra()))) {
            return "Sintegra: " + cliente.getSintegra();
        }

        // Verifica CNAE
        if (!config.isCnaePermitido(cliente.getCnae())) {
            return "CNAE não permitido: " + cliente.getCnae();
        }

        return null;
    }

    /**
     * Verifica se a empresa foi fundada há menos de X meses (configurável).
     */
    public boolean isFundacaoRecente(Cliente cliente) {
        if (cliente.getDataFundacao() == null) {
            return false;
        }
        Configuracao config = getConfig();
        long meses = ChronoUnit.MONTHS.between(cliente.getDataFundacao(), LocalDate.now());
        return meses < config.getMesesFundacaoThreshold();
    }

    /**
     * Verifica se algum protesto do cliente ultrapassa o threshold configurado.
     */
    public boolean hasProtestoAcima(Cliente cliente) {
        Configuracao config = getConfig();
        BigDecimal threshold = config.getProtestoThresholdAntecipado();
        List<Protesto> protestos = protestoRepository.findByClienteId(cliente.getId());
        return protestos.stream()
                .anyMatch(p -> p.getValor() != null && p.getValor().compareTo(threshold) > 0);
    }

    /**
     * Verifica se a loja foi aberta há menos de X meses (configurável).
     */
    public boolean isLojaRecente(Cliente cliente) {
        if (cliente.getDataAberturaLoja() == null) {
            return false;
        }
        Configuracao config = getConfig();
        long meses = ChronoUnit.MONTHS.between(cliente.getDataAberturaLoja(), LocalDate.now());
        return meses < config.getMesesLojaThreshold();
    }

    /**
     * Verifica se o total de restrições (pefin + protestos + ações + cheques)
     * em valor ultrapassa o threshold configurado.
     */
    public boolean hasRestricaoAcima(Cliente cliente) {
        Configuracao config = getConfig();
        BigDecimal threshold = config.getRestricaoThresholdAntecipado();

        BigDecimal totalPefin = pefinRepository.findByClienteId(cliente.getId()).stream()
                .map(Pefin::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProtesto = protestoRepository.findByClienteId(cliente.getId()).stream()
                .map(Protesto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = totalPefin.add(totalProtesto);

        return total.compareTo(threshold) > 0;
    }
}
