package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertaService.
 *
 * Tests cover all 6 alert types:
 * 1. SIMEI > LIMITE
 * 2. GRUPO > X SIMEIS
 * 3. PEDIDO > LIMITE
 * 4. TOTAL > LIMITE
 * 5. RESTRIÇÕES (X)
 * 6. SCORE BAIXO
 * 7. No alerts scenario
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock
    private ConfiguracaoRepository configuracaoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private AlertaService alertaService;

    private Configuracao configuracao;
    private GrupoEconomico grupo;
    private Cliente cliente;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        // Setup default configuration
        configuracao = new Configuracao();
        configuracao.setId(1L);
        configuracao.setLimiteSimei(new BigDecimal("35000"));
        configuracao.setMaxSimeisPorGrupo(2);
        configuracao.setScoreBaixoThreshold(300);

        // Setup grupo economico
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(new BigDecimal("50000"));
        grupo.setLimiteDisponivel(new BigDecimal("50000"));
        grupo.setClientes(new ArrayList<>());

        // Setup cliente
        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setCnpj("12345678000190");
        cliente.setRazaoSocial("Cliente Teste Ltda");
        cliente.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente.setSimei(false);
        cliente.setGrupoEconomico(grupo);
        cliente.setPedidos(new ArrayList<>());
        cliente.setPefins(new ArrayList<>());
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        // Setup pedido
        pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumero("PED001");
        pedido.setData(LocalDate.now());
        pedido.setValor(new BigDecimal("30000"));
        pedido.setCliente(cliente);

        // Add pedido to cliente
        cliente.getPedidos().add(pedido);

        // Add cliente to grupo
        grupo.getClientes().add(cliente);

        // Mock configuracao repository
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.of(configuracao));
    }

    /**
     * Test 1: SIMEI > LIMITE
     *
     * Setup: cliente.simei=true, pedido.valor=40000, config.limiteSimei=35000
     * Expected: alerts contém "SIMEI > LIMITE"
     */
    @Test
    void calcularAlertas_simeiAcima35k_retornaAlerta() {
        // Arrange
        cliente.setSimei(true);
        pedido.setValor(new BigDecimal("40000"));

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("SIMEI > LIMITE"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 2: GRUPO > X SIMEIS
     *
     * Setup: grupo com 3 clientes SIMEI, todos com pedidos, config.maxSimeis=2
     * Expected: alerts contém "GRUPO > 2 SIMEIS"
     */
    @Test
    void calcularAlertas_grupoComTresSimeisComPedido_retornaAlerta() {
        // Arrange
        // Cliente 1 - SIMEI com pedido (já existe no setup)
        cliente.setSimei(true);

        // Cliente 2 - SIMEI com pedido
        Cliente cliente2 = new Cliente();
        cliente2.setId(2L);
        cliente2.setCnpj("98765432000190");
        cliente2.setRazaoSocial("Cliente SIMEI 2");
        cliente2.setTipoCliente(TipoCliente.NOVO);
        cliente2.setSimei(true);
        cliente2.setGrupoEconomico(grupo);
        cliente2.setPedidos(new ArrayList<>());
        cliente2.setPefins(new ArrayList<>());
        cliente2.setProtestos(new ArrayList<>());
        cliente2.setAcoesJudiciais(new ArrayList<>());
        cliente2.setCheques(new ArrayList<>());

        Pedido pedido2 = new Pedido();
        pedido2.setId(2L);
        pedido2.setNumero("PED002");
        pedido2.setData(LocalDate.now());
        pedido2.setValor(new BigDecimal("20000"));
        pedido2.setCliente(cliente2);
        cliente2.getPedidos().add(pedido2);

        // Cliente 3 - SIMEI com pedido
        Cliente cliente3 = new Cliente();
        cliente3.setId(3L);
        cliente3.setCnpj("11122233000190");
        cliente3.setRazaoSocial("Cliente SIMEI 3");
        cliente3.setTipoCliente(TipoCliente.ANTECIPADO);
        cliente3.setSimei(true);
        cliente3.setGrupoEconomico(grupo);
        cliente3.setPedidos(new ArrayList<>());
        cliente3.setPefins(new ArrayList<>());
        cliente3.setProtestos(new ArrayList<>());
        cliente3.setAcoesJudiciais(new ArrayList<>());
        cliente3.setCheques(new ArrayList<>());

        Pedido pedido3 = new Pedido();
        pedido3.setId(3L);
        pedido3.setNumero("PED003");
        pedido3.setData(LocalDate.now());
        pedido3.setValor(new BigDecimal("15000"));
        pedido3.setCliente(cliente3);
        cliente3.getPedidos().add(pedido3);

        // Add to grupo
        grupo.getClientes().add(cliente2);
        grupo.getClientes().add(cliente3);

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("GRUPO > 2 SIMEIS"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 3: PEDIDO > LIMITE
     *
     * Setup: pedido.valor=60000, grupo.limiteAprovado=50000
     * Expected: alerts contém "PEDIDO > LIMITE"
     */
    @Test
    void calcularAlertas_pedidoAcimaDoLimite_retornaAlerta() {
        // Arrange
        pedido.setValor(new BigDecimal("60000"));
        grupo.setLimiteAprovado(new BigDecimal("50000"));

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("PEDIDO > LIMITE"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 4: TOTAL > LIMITE
     *
     * Setup: grupo com múltiplos pedidos totalizando 80000, grupo.limiteAprovado=50000
     * Expected: alerts contém "TOTAL > LIMITE"
     */
    @Test
    void calcularAlertas_totalPedidosAcimaLimite_retornaAlerta() {
        // Arrange
        // Cliente 1 já tem pedido de 30000 (do setup)
        pedido.setValor(new BigDecimal("30000"));

        // Cliente 2 com pedido de 50000
        Cliente cliente2 = new Cliente();
        cliente2.setId(2L);
        cliente2.setCnpj("98765432000190");
        cliente2.setRazaoSocial("Cliente 2");
        cliente2.setTipoCliente(TipoCliente.NOVO);
        cliente2.setSimei(false);
        cliente2.setGrupoEconomico(grupo);
        cliente2.setPedidos(new ArrayList<>());
        cliente2.setPefins(new ArrayList<>());
        cliente2.setProtestos(new ArrayList<>());
        cliente2.setAcoesJudiciais(new ArrayList<>());
        cliente2.setCheques(new ArrayList<>());

        Pedido pedido2 = new Pedido();
        pedido2.setId(2L);
        pedido2.setNumero("PED002");
        pedido2.setData(LocalDate.now());
        pedido2.setValor(new BigDecimal("50000"));
        pedido2.setCliente(cliente2);
        cliente2.getPedidos().add(pedido2);

        grupo.getClientes().add(cliente2);
        grupo.setLimiteAprovado(new BigDecimal("50000"));

        // Total = 30000 + 50000 = 80000 > 50000

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("TOTAL > LIMITE"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 5: RESTRIÇÕES (X)
     *
     * Setup: cliente com 2 protestos, 1 pefin
     * Expected: alerts contém "RESTRIÇÕES (3)"
     */
    @Test
    void calcularAlertas_comRestricoes_retornaAlerta() {
        // Arrange
        // Add 2 protestos
        Protesto protesto1 = new Protesto();
        protesto1.setId(1L);
        protesto1.setCliente(cliente);
        protesto1.setCartorio("1o Cartorio");
        protesto1.setValor(new BigDecimal("5000"));
        protesto1.setDataProtesto(LocalDate.now().minusMonths(6));

        Protesto protesto2 = new Protesto();
        protesto2.setId(2L);
        protesto2.setCliente(cliente);
        protesto2.setCartorio("2o Cartorio");
        protesto2.setValor(new BigDecimal("3000"));
        protesto2.setDataProtesto(LocalDate.now().minusMonths(3));

        cliente.getProtestos().add(protesto1);
        cliente.getProtestos().add(protesto2);

        // Add 1 pefin
        Pefin pefin = new Pefin();
        pefin.setId(1L);
        pefin.setCliente(cliente);
        pefin.setOrigem("Serasa");
        pefin.setValor(new BigDecimal("2000"));
        pefin.setDataOcorrencia(LocalDate.now().minusMonths(4));

        cliente.getPefins().add(pefin);

        // Total restricoes = 2 protestos + 1 pefin = 3

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("RESTRIÇÕES (3)"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 6: SCORE BAIXO
     *
     * Setup: cliente.scoreBoaVista=250, config.scoreBaixoThreshold=300
     * Expected: alerts contém "SCORE BAIXO"
     */
    @Test
    void calcularAlertas_scoreBaixo_retornaAlerta() {
        // Arrange
        cliente.setScoreBoaVista(250);
        configuracao.setScoreBaixoThreshold(300);

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("SCORE BAIXO"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 7: SEM PROBLEMAS
     *
     * Setup: pedido dentro dos limites, sem restrições, score bom
     * Expected: alerts.isEmpty() == true
     */
    @Test
    void calcularAlertas_semProblemas_retornaListaVazia() {
        // Arrange
        cliente.setSimei(false);
        cliente.setScoreBoaVista(500); // Score bom (acima do threshold de 300)
        pedido.setValor(new BigDecimal("30000")); // Abaixo do limite do grupo (50000)
        grupo.setLimiteAprovado(new BigDecimal("100000")); // Limite alto

        // Sem restrições (listas já estão vazias no setup)

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.isEmpty());
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 8: MÚLTIPLOS ALERTAS
     *
     * Setup: pedido com múltiplos problemas
     * Expected: alerts contém múltiplos alertas
     */
    @Test
    void calcularAlertas_multiplosproblemas_retornaMultiplosAlertas() {
        // Arrange
        cliente.setSimei(true);
        cliente.setScoreBoaVista(200);
        pedido.setValor(new BigDecimal("60000")); // Acima de limiteSimei (35k) e acima de limiteAprovado (50k)
        grupo.setLimiteAprovado(new BigDecimal("50000"));

        // Add restricoes
        Protesto protesto = new Protesto();
        protesto.setId(1L);
        protesto.setCliente(cliente);
        protesto.setCartorio("Cartorio Central");
        protesto.setValor(new BigDecimal("1000"));
        protesto.setDataProtesto(LocalDate.now());
        cliente.getProtestos().add(protesto);

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertTrue(alerts.contains("SIMEI > LIMITE"));
        assertTrue(alerts.contains("PEDIDO > LIMITE"));
        assertTrue(alerts.contains("RESTRIÇÕES (1)"));
        assertTrue(alerts.contains("SCORE BAIXO"));
        assertTrue(alerts.size() >= 4);
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 9: SIMEI NO LIMITE (não acima)
     *
     * Setup: cliente SIMEI com pedido exatamente igual ao limite
     * Expected: NÃO deve retornar alerta SIMEI > LIMITE
     */
    @Test
    void calcularAlertas_simeiNoLimiteExato_naoRetornaAlerta() {
        // Arrange
        cliente.setSimei(true);
        pedido.setValor(new BigDecimal("35000")); // Exatamente igual ao limite

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertFalse(alerts.contains("SIMEI > LIMITE"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 10: CLIENTE SIMEI SEM FLAG
     *
     * Setup: cliente.simei=null (não Boolean.TRUE)
     * Expected: NÃO deve retornar alerta SIMEI mesmo com valor alto
     */
    @Test
    void calcularAlertas_clienteSemFlagSimei_naoRetornaAlertaSimei() {
        // Arrange
        cliente.setSimei(null);
        pedido.setValor(new BigDecimal("50000"));

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertFalse(alerts.contains("SIMEI > LIMITE"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 11: SCORE NULL
     *
     * Setup: cliente.scoreBoaVista=null
     * Expected: NÃO deve retornar alerta SCORE BAIXO
     */
    @Test
    void calcularAlertas_scoreNull_naoRetornaAlertaScore() {
        // Arrange
        cliente.setScoreBoaVista(null);

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        assertFalse(alerts.contains("SCORE BAIXO"));
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 12: CONFIGURAÇÃO NÃO ENCONTRADA
     *
     * Setup: configuracaoRepository retorna empty
     * Expected: Deve lançar RuntimeException
     */
    @Test
    void calcularAlertas_configuracaoNaoEncontrada_lancaExcecao() {
        // Arrange
        when(configuracaoRepository.findById(1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            alertaService.calcularAlertas(pedido);
        });

        assertEquals("Configuração não encontrada", exception.getMessage());
        verify(configuracaoRepository).findById(1L);
    }

    /**
     * Test 13: GRUPO COM SIMEIS SEM PEDIDOS
     *
     * Setup: grupo com 3 clientes SIMEI, mas sem pedidos
     * Expected: NÃO deve retornar alerta GRUPO > X SIMEIS
     */
    @Test
    void calcularAlertas_grupoComSimeisSemPedidos_naoRetornaAlerta() {
        // Arrange
        cliente.setSimei(true);
        cliente.getPedidos().clear(); // Remove o pedido

        // Cliente 2 - SIMEI sem pedido
        Cliente cliente2 = new Cliente();
        cliente2.setId(2L);
        cliente2.setCnpj("98765432000190");
        cliente2.setRazaoSocial("Cliente SIMEI 2");
        cliente2.setTipoCliente(TipoCliente.NOVO);
        cliente2.setSimei(true);
        cliente2.setGrupoEconomico(grupo);
        cliente2.setPedidos(new ArrayList<>()); // Sem pedidos

        // Cliente 3 - SIMEI sem pedido
        Cliente cliente3 = new Cliente();
        cliente3.setId(3L);
        cliente3.setCnpj("11122233000190");
        cliente3.setRazaoSocial("Cliente SIMEI 3");
        cliente3.setTipoCliente(TipoCliente.ANTECIPADO);
        cliente3.setSimei(true);
        cliente3.setGrupoEconomico(grupo);
        cliente3.setPedidos(new ArrayList<>()); // Sem pedidos

        grupo.getClientes().add(cliente2);
        grupo.getClientes().add(cliente3);

        // Re-add pedido to cliente
        cliente.getPedidos().add(pedido);

        // Act
        List<String> alerts = alertaService.calcularAlertas(pedido);

        // Assert
        // Apenas 1 SIMEI com pedido, não deve retornar alerta
        assertFalse(alerts.contains("GRUPO > 2 SIMEIS"));
        verify(configuracaoRepository).findById(1L);
    }
}
