package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.Protesto;
import AnaliseCredito.Analise_de_Credito.domain.model.Pefin;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteNovoValidationServiceTest {

    @Mock
    private ConfiguracaoRepository configuracaoRepository;

    @Mock
    private ProtestoRepository protestoRepository;

    @Mock
    private PefinRepository pefinRepository;

    @Mock
    private AcaoJudicialRepository acaoJudicialRepository;

    @Mock
    private ChequeRepository chequeRepository;

    @InjectMocks
    private ClienteNovoValidationService validationService;

    private Configuracao config;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        config = new Configuracao();
        config.setId(1L);
        config.setProtestoThresholdAntecipado(new BigDecimal("1000"));
        config.setRestricaoThresholdAntecipado(new BigDecimal("1000"));
        config.setMesesLojaThreshold(10);
        config.setMesesFundacaoThreshold(12);

        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setCnpj("12345678000190");
        cliente.setRazaoSocial("Teste LTDA");
    }

    @Test
    void hasConsultaData_comDados_retornaTrue() {
        cliente.setStatusReceita("ATIVA");
        cliente.setSintegra("HABILITADO");
        assertTrue(validationService.hasConsultaData(cliente));
    }

    @Test
    void hasConsultaData_semDados_retornaFalse() {
        assertFalse(validationService.hasConsultaData(cliente));
    }

    @Test
    void hasConsultaData_parcial_retornaFalse() {
        cliente.setStatusReceita("ATIVA");
        assertFalse(validationService.hasConsultaData(cliente));
    }

    @Test
    void validarCadastral_receitaBaixada_retornaMotivo() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setStatusReceita("BAIXADA");
        cliente.setSintegra("HABILITADO");

        String motivo = validationService.validarCadastral(cliente);
        assertNotNull(motivo);
        assertTrue(motivo.contains("BAIXADA"));
    }

    @Test
    void validarCadastral_sintegraInabilitado_retornaMotivo() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setStatusReceita("ATIVA");
        cliente.setSintegra("INABILITADO");

        String motivo = validationService.validarCadastral(cliente);
        assertNotNull(motivo);
        assertTrue(motivo.contains("INABILITADO"));
    }

    @Test
    void validarCadastral_cnaeNaoPermitido_retornaMotivo() {
        config.setCnaesPermitidos("4781400,4782201");
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setStatusReceita("ATIVA");
        cliente.setSintegra("HABILITADO");
        cliente.setCnae("9999999");

        String motivo = validationService.validarCadastral(cliente);
        assertNotNull(motivo);
        assertTrue(motivo.contains("CNAE"));
    }

    @Test
    void validarCadastral_tudoOk_retornaNull() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setStatusReceita("ATIVA");
        cliente.setSintegra("HABILITADO");
        cliente.setCnae("4781400");

        assertNull(validationService.validarCadastral(cliente));
    }

    @Test
    void isFundacaoRecente_menosDe12Meses_retornaTrue() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setDataFundacao(LocalDate.now().minusMonths(6));

        assertTrue(validationService.isFundacaoRecente(cliente));
    }

    @Test
    void isFundacaoRecente_maisDe12Meses_retornaFalse() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setDataFundacao(LocalDate.now().minusYears(2));

        assertFalse(validationService.isFundacaoRecente(cliente));
    }

    @Test
    void isFundacaoRecente_semData_retornaFalse() {
        assertFalse(validationService.isFundacaoRecente(cliente));
    }

    @Test
    void hasProtestoAcima_comProtestoAlto_retornaTrue() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        Protesto protesto = new Protesto();
        protesto.setValor(new BigDecimal("1500"));
        when(protestoRepository.findByClienteId(1L)).thenReturn(List.of(protesto));

        assertTrue(validationService.hasProtestoAcima(cliente));
    }

    @Test
    void hasProtestoAcima_comProtestoBaixo_retornaFalse() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        Protesto protesto = new Protesto();
        protesto.setValor(new BigDecimal("500"));
        when(protestoRepository.findByClienteId(1L)).thenReturn(List.of(protesto));

        assertFalse(validationService.hasProtestoAcima(cliente));
    }

    @Test
    void hasProtestoAcima_semProtestos_retornaFalse() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        when(protestoRepository.findByClienteId(1L)).thenReturn(Collections.emptyList());

        assertFalse(validationService.hasProtestoAcima(cliente));
    }

    @Test
    void isLojaRecente_menosDe10Meses_retornaTrue() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setDataAberturaLoja(LocalDate.now().minusMonths(5));

        assertTrue(validationService.isLojaRecente(cliente));
    }

    @Test
    void isLojaRecente_maisDe10Meses_retornaFalse() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));
        cliente.setDataAberturaLoja(LocalDate.now().minusMonths(15));

        assertFalse(validationService.isLojaRecente(cliente));
    }

    @Test
    void hasRestricaoAcima_totalAlto_retornaTrue() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));

        Pefin pefin = new Pefin();
        pefin.setValor(new BigDecimal("600"));
        when(pefinRepository.findByClienteId(1L)).thenReturn(List.of(pefin));

        Protesto protesto = new Protesto();
        protesto.setValor(new BigDecimal("500"));
        when(protestoRepository.findByClienteId(1L)).thenReturn(List.of(protesto));

        assertTrue(validationService.hasRestricaoAcima(cliente));
    }

    @Test
    void hasRestricaoAcima_totalBaixo_retornaFalse() {
        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(config));

        Pefin pefin = new Pefin();
        pefin.setValor(new BigDecimal("200"));
        when(pefinRepository.findByClienteId(1L)).thenReturn(List.of(pefin));

        Protesto protesto = new Protesto();
        protesto.setValor(new BigDecimal("300"));
        when(protestoRepository.findByClienteId(1L)).thenReturn(List.of(protesto));

        assertFalse(validationService.hasRestricaoAcima(cliente));
    }
}
