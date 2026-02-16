package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.DadosBI;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.DadosBIRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScoringService.
 *
 * Tests cover:
 * 1. Score-based multiplier calculation (high score = 1.5x)
 * 2. SIMEI cap application
 * 3. Empty BI data handling
 * 4. Multiple collections handling (taking the highest credit)
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private DadosBIRepository dadosBIRepository;

    @Mock
    private ConfiguracaoRepository configuracaoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ScoringService scoringService;

    private Configuracao configuracao;
    private GrupoEconomico grupo;

    @BeforeEach
    void setUp() {
        // Setup default configuration with standard multipliers
        configuracao = new Configuracao();
        configuracao.setId(1L);
        configuracao.setScoreAltoMultiplicador(new BigDecimal("1.5"));       // >= 800
        configuracao.setScoreMedioMultiplicador(new BigDecimal("1.2"));      // >= 600
        configuracao.setScoreNormalMultiplicador(new BigDecimal("1.0"));     // >= 400
        configuracao.setScoreBaixoMultiplicador(new BigDecimal("0.7"));      // < 400
        configuracao.setLimiteSimei(new BigDecimal("35000"));

        // Setup grupo economico
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");
        grupo.setClientes(new ArrayList<>());
    }

    /**
     * Test 1: Score alto (>= 800) deve retornar multiplicador 1.5
     *
     * Setup: DadosBI com score=850, credito=50000
     * Expected: limite = 50000 * 1.5 = 75000
     */
    @Test
    void calcularLimiteSugerido_scoreAlto_retornaMultiplicador15() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("50000"));
        dadosBI.setScore(850);  // Score alto >= 800
        dadosBI.setDataImportacao(LocalDateTime.now());

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        assertEquals(0, new BigDecimal("75000").compareTo(limite));
        verify(dadosBIRepository).findByGrupoEconomicoIdOrderByColecaoDesc(1L);
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 2: Cliente SIMEI com pedido deve aplicar cap de 35000
     *
     * Setup: cliente SIMEI com pedido, limite calculado = 40000, config.limiteSimei = 35000
     * Expected: limite = 35000 (cap aplicado)
     */
    @Test
    void calcularLimiteSugerido_simeiComPedido_aplicaCap() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("40000"));  // Credito que geraria limite > 35000
        dadosBI.setScore(400);  // Score normal (multiplicador 1.0)
        dadosBI.setDataImportacao(LocalDateTime.now());

        // Create SIMEI client with pedido
        Cliente clienteSimei = new Cliente();
        clienteSimei.setId(1L);
        clienteSimei.setCnpj("12345678000190");
        clienteSimei.setRazaoSocial("Cliente SIMEI Teste");
        clienteSimei.setSimei(true);
        clienteSimei.setGrupoEconomico(grupo);

        // Create pedido
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumero("PED001");
        pedido.setData(LocalDate.now());
        pedido.setValor(new BigDecimal("10000"));
        pedido.setCliente(clienteSimei);

        clienteSimei.setPedidos(Collections.singletonList(pedido));
        grupo.setClientes(Collections.singletonList(clienteSimei));

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // Without SIMEI cap: 40000 * 1.0 = 40000
        // With SIMEI cap: 35000
        assertEquals(new BigDecimal("35000"), limite);
        verify(dadosBIRepository).findByGrupoEconomicoIdOrderByColecaoDesc(1L);
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 3: Sem dados de BI deve retornar zero
     *
     * Setup: Nenhum dado de BI disponível
     * Expected: limite = 0
     */
    @Test
    void calcularLimiteSugerido_semDadosBI_retornaZero() {
        // Arrange
        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.emptyList());

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        assertEquals(BigDecimal.ZERO, limite);
        verify(dadosBIRepository).findByGrupoEconomicoIdOrderByColecaoDesc(1L);
        verify(configuracaoRepository, never()).findById(anyLong());
    }

    /**
     * Test 4: Com duas coleções, deve pegar o maior crédito
     *
     * Setup: 2 coleções - colecao1 com credito=30000, colecao2 com credito=45000
     * Expected: Usa maior credito (45000) e score da mais recente
     */
    @Test
    void calcularLimiteSugerido_duasColecoes_pegaMaiorCredito() {
        // Arrange
        // Coleção mais recente (202602) com crédito menor
        DadosBI colecaoRecente = new DadosBI();
        colecaoRecente.setId(2L);
        colecaoRecente.setGrupoEconomico(grupo);
        colecaoRecente.setColecao(202602);
        colecaoRecente.setCredito(new BigDecimal("30000"));  // Menor credito
        colecaoRecente.setScore(600);  // Score médio (multiplicador 1.2)
        colecaoRecente.setDataImportacao(LocalDateTime.now());

        // Coleção mais antiga (202601) com crédito maior
        DadosBI colecaoAntiga = new DadosBI();
        colecaoAntiga.setId(1L);
        colecaoAntiga.setGrupoEconomico(grupo);
        colecaoAntiga.setColecao(202601);
        colecaoAntiga.setCredito(new BigDecimal("45000"));  // Maior credito
        colecaoAntiga.setScore(500);  // Score diferente, mas não será usado
        colecaoAntiga.setDataImportacao(LocalDateTime.now().minusMonths(1));

        // Return in DESC order (most recent first)
        List<DadosBI> dadosBI = Arrays.asList(colecaoRecente, colecaoAntiga);

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(dadosBI);
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // Should use: maior credito (45000) * score da recente (600 -> 1.2) = 54000
        assertEquals(0, new BigDecimal("54000").compareTo(limite));
        verify(dadosBIRepository).findByGrupoEconomicoIdOrderByColecaoDesc(1L);
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 5: Score médio (600-799) deve retornar multiplicador 1.2
     */
    @Test
    void calcularLimiteSugerido_scoreMedio_retornaMultiplicador12() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("50000"));
        dadosBI.setScore(700);  // Score médio >= 600 e < 800
        dadosBI.setDataImportacao(LocalDateTime.now());

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // 50000 * 1.2 = 60000
        assertEquals(0, new BigDecimal("60000").compareTo(limite));
    }

    /**
     * Test 6: Score normal (400-599) deve retornar multiplicador 1.0
     */
    @Test
    void calcularLimiteSugerido_scoreNormal_retornaMultiplicador10() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("50000"));
        dadosBI.setScore(500);  // Score normal >= 400 e < 600
        dadosBI.setDataImportacao(LocalDateTime.now());

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // 50000 * 1.0 = 50000
        assertEquals(0, new BigDecimal("50000").compareTo(limite));
    }

    /**
     * Test 7: Score baixo (< 400) deve retornar multiplicador 0.7
     */
    @Test
    void calcularLimiteSugerido_scoreBaixo_retornaMultiplicador07() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("50000"));
        dadosBI.setScore(350);  // Score baixo < 400
        dadosBI.setDataImportacao(LocalDateTime.now());

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // 50000 * 0.7 = 35000
        assertEquals(0, new BigDecimal("35000").compareTo(limite));
    }

    /**
     * Test 8: Cliente SIMEI sem pedido não deve aplicar cap
     */
    @Test
    void calcularLimiteSugerido_simeiSemPedido_naoAplicaCap() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("40000"));
        dadosBI.setScore(400);  // Score normal (multiplicador 1.0)
        dadosBI.setDataImportacao(LocalDateTime.now());

        // Create SIMEI client WITHOUT pedido
        Cliente clienteSimei = new Cliente();
        clienteSimei.setId(1L);
        clienteSimei.setCnpj("12345678000190");
        clienteSimei.setRazaoSocial("Cliente SIMEI Teste");
        clienteSimei.setSimei(true);
        clienteSimei.setGrupoEconomico(grupo);
        clienteSimei.setPedidos(new ArrayList<>()); // Empty pedidos

        grupo.setClientes(Collections.singletonList(clienteSimei));

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));

        // Act
        BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);

        // Assert
        // Without SIMEI cap: 40000 * 1.0 = 40000 (no cap applied)
        assertEquals(0, new BigDecimal("40000").compareTo(limite));
    }

    /**
     * Test 9: Configuração não encontrada deve lançar exceção
     */
    @Test
    void calcularLimiteSugerido_configuracaoNaoEncontrada_lancaExcecao() {
        // Arrange
        DadosBI dadosBI = new DadosBI();
        dadosBI.setId(1L);
        dadosBI.setGrupoEconomico(grupo);
        dadosBI.setColecao(202601);
        dadosBI.setCredito(new BigDecimal("50000"));
        dadosBI.setScore(500);
        dadosBI.setDataImportacao(LocalDateTime.now());

        when(dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(1L))
                .thenReturn(Collections.singletonList(dadosBI));
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            scoringService.calcularLimiteSugerido(grupo);
        });

        assertEquals("Configuração não encontrada", exception.getMessage());
    }
}
