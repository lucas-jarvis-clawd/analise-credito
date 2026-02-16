package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Analise;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Configuracao;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.AnaliseRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ConfiguracaoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowService.
 *
 * Tests cover:
 * 1. Valid workflow transitions
 * 2. Invalid transition rejection
 * 3. Alçada (manager approval) rules
 * 4. Limite grupo update on finalization
 * 5. Metadata tracking (timestamps, analista)
 * 6. State-specific logic
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private AnaliseRepository analiseRepository;

    @Mock
    private ConfiguracaoRepository configuracaoRepository;

    @Mock
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private Configuracao configuracao;
    private GrupoEconomico grupo;
    private Cliente cliente;
    private Pedido pedido;
    private Analise analise;

    @BeforeEach
    void setUp() {
        // Setup configuracao
        configuracao = new Configuracao();
        configuracao.setId(1L);
        configuracao.setValorAprovacaoGestor(new BigDecimal("100000"));
        configuracao.setTotalGrupoAprovacaoGestor(new BigDecimal("200000"));
        configuracao.setRestricoesAprovacaoGestor(5);

        // Setup grupo economico
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(BigDecimal.ZERO);
        grupo.setLimiteDisponivel(BigDecimal.ZERO);
        grupo.setClientes(new ArrayList<>());

        // Setup cliente
        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setCnpj("12345678000190");
        cliente.setRazaoSocial("Cliente Teste");
        cliente.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente.setSimei(false);
        cliente.setGrupoEconomico(grupo);
        cliente.setPedidos(new ArrayList<>());
        cliente.setPefins(new ArrayList<>());
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        grupo.setClientes(Collections.singletonList(cliente));

        // Setup pedido - BASE_PRAZO workflow
        pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumero("PED001");
        pedido.setData(LocalDate.now());
        pedido.setValor(new BigDecimal("50000"));
        pedido.setBloqueio("00");  // BASE_PRAZO
        pedido.setWorkflow(TipoWorkflow.BASE_PRAZO);
        pedido.setCliente(cliente);

        cliente.getPedidos().add(pedido);

        // Setup analise
        analise = new Analise();
        analise.setId(1L);
        analise.setPedido(pedido);
        analise.setClienteId(cliente.getId());
        analise.setGrupoEconomicoId(grupo.getId());
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);
        analise.setRequerAprovacaoGestor(false);

        pedido.setAnalise(analise);
    }

    /**
     * Test 1: Transição válida PENDENTE → EM_ANALISE_FINANCEIRO deve funcionar
     */
    @Test
    void transicionar_pendenteParaEmAnalise_sucesso() {
        // Arrange
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.EM_ANALISE_FINANCEIRO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.EM_ANALISE_FINANCEIRO, analise.getStatusWorkflow());
        assertEquals("analista@teste.com", analise.getAnalistaResponsavel());
        assertNotNull(analise.getDataInicio());
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 2: Transição inválida deve lançar exceção
     */
    @Test
    void transicionar_transicaoInvalida_lancaExcecao() {
        // Arrange - PENDENTE não pode ir direto para FINALIZADO
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");
        });

        assertTrue(exception.getMessage().contains("Transição inválida"));
        verify(analiseRepository, never()).save(any());
    }

    /**
     * Test 3: Transição para PARECER_APROVADO deve verificar alçada
     */
    @Test
    void transicionar_paraParecer_verificaAlcada() {
        // Arrange
        analise.setStatusWorkflow(StatusWorkflow.EM_ANALISE_FINANCEIRO);
        analise.setDataInicio(LocalDateTime.now());

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.PARECER_APROVADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.PARECER_APROVADO, analise.getStatusWorkflow());
        assertFalse(analise.getRequerAprovacaoGestor()); // Valor 50k < 100k threshold
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 4: Transição para FINALIZADO deve atualizar limite do grupo
     */
    @Test
    void transicionar_paraFinalizado_atualizaLimiteGrupo() {
        // Arrange
        analise.setStatusWorkflow(StatusWorkflow.PARECER_APROVADO);
        analise.setDataInicio(LocalDateTime.now());
        analise.setLimiteAprovado(new BigDecimal("100000"));

        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);
        when(grupoEconomicoRepository.save(any(GrupoEconomico.class))).thenReturn(grupo);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.FINALIZADO, analise.getStatusWorkflow());
        assertNotNull(analise.getDataFim());

        // Verify grupo was updated
        ArgumentCaptor<GrupoEconomico> grupoCaptor = ArgumentCaptor.forClass(GrupoEconomico.class);
        verify(grupoEconomicoRepository).save(grupoCaptor.capture());
        GrupoEconomico grupoSalvo = grupoCaptor.getValue();
        assertEquals(0, new BigDecimal("100000").compareTo(grupoSalvo.getLimiteAprovado()));
    }

    /**
     * Test 5: Valor alto deve requerer aprovação de gestor
     */
    @Test
    void requerAprovacaoGestor_valorAlto_retornaTrue() {
        // Arrange
        pedido.setValor(new BigDecimal("150000"));  // > 100000 threshold

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        // Act
        boolean requer = workflowService.requerAprovacaoGestor(analise);

        // Assert
        assertTrue(requer);
    }

    /**
     * Test 6: Restrições altas devem requerer aprovação de gestor
     */
    @Test
    void requerAprovacaoGestor_restricoesAltas_retornaTrue() {
        // Arrange
        // Add 5 pefins to reach restricoes threshold
        for (int i = 0; i < 5; i++) {
            cliente.getPefins().add(null);  // Just counting, don't need actual objects
        }

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        // Act
        boolean requer = workflowService.requerAprovacaoGestor(analise);

        // Assert
        assertTrue(requer);
    }

    /**
     * Test 7: Total do grupo alto deve requerer aprovação de gestor
     */
    @Test
    void requerAprovacaoGestor_totalGrupoAlto_retornaTrue() {
        // Arrange
        // Create another pedido to increase total
        Pedido pedido2 = new Pedido();
        pedido2.setId(2L);
        pedido2.setNumero("PED002");
        pedido2.setData(LocalDate.now());
        pedido2.setValor(new BigDecimal("180000"));  // Total will be 50k + 180k = 230k > 200k
        pedido2.setCliente(cliente);

        Analise analise2 = new Analise();
        analise2.setId(2L);
        analise2.setPedido(pedido2);
        analise2.setClienteId(cliente.getId());
        analise2.setGrupoEconomicoId(grupo.getId());
        analise2.setStatusWorkflow(StatusWorkflow.EM_ANALISE_FINANCEIRO);
        analise2.setDataInicio(LocalDateTime.now());
        // dataFim == null means still open

        pedido2.setAnalise(analise2);
        cliente.getPedidos().add(pedido2);

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        // Act
        boolean requer = workflowService.requerAprovacaoGestor(analise);

        // Assert
        assertTrue(requer);
    }

    /**
     * Test 8: Valores normais não devem requerer aprovação de gestor
     */
    @Test
    void requerAprovacaoGestor_valoresNormais_retornaFalse() {
        // Arrange
        pedido.setValor(new BigDecimal("50000"));  // < 100000

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        // Act
        boolean requer = workflowService.requerAprovacaoGestor(analise);

        // Assert
        assertFalse(requer);
    }

    /**
     * Test 9: Workflow CLIENTE_NOVO - transição PENDENTE → FAZER_CONSULTAS
     */
    @Test
    void transicionar_clienteNovo_pendenteParaFazerConsultas_sucesso() {
        // Arrange
        pedido.setBloqueio("80");  // CLIENTE_NOVO
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.FAZER_CONSULTAS, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.FAZER_CONSULTAS, analise.getStatusWorkflow());
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 10: Workflow CLIENTE_NOVO - não pode ir direto para EM_ANALISE_FINANCEIRO
     */
    @Test
    void transicionar_clienteNovo_transicaoInvalida_lancaExcecao() {
        // Arrange
        pedido.setBloqueio("80");  // CLIENTE_NOVO
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);

        // Act & Assert - Cannot go directly to EM_ANALISE_FINANCEIRO
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            workflowService.transicionar(analise, StatusWorkflow.EM_ANALISE_FINANCEIRO, "analista@teste.com");
        });

        assertTrue(exception.getMessage().contains("Transição inválida"));
    }

    /**
     * Test 11: Workflow CLIENTE_NOVO - EM_ANALISE_CLIENTE_NOVO → PARECER_APROVADO
     */
    @Test
    void transicionar_clienteNovo_emAnaliseParaParecer_sucesso() {
        // Arrange
        pedido.setBloqueio("80");  // CLIENTE_NOVO
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.EM_ANALISE_CLIENTE_NOVO);

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.PARECER_APROVADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.PARECER_APROVADO, analise.getStatusWorkflow());
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 12: isTransicaoValida - deve retornar true para transições válidas
     */
    @Test
    void isTransicaoValida_transicaoPermitida_retornaTrue() {
        // Act & Assert
        assertTrue(workflowService.isTransicaoValida(
            StatusWorkflow.PENDENTE,
            StatusWorkflow.EM_ANALISE_FINANCEIRO,
            TipoWorkflow.BASE_PRAZO
        ));

        assertTrue(workflowService.isTransicaoValida(
            StatusWorkflow.PENDENTE,
            StatusWorkflow.FAZER_CONSULTAS,
            TipoWorkflow.CLIENTE_NOVO
        ));
    }

    /**
     * Test 13: isTransicaoValida - deve retornar false para mesmo estado
     */
    @Test
    void isTransicaoValida_mesmoEstado_retornaFalse() {
        // Act & Assert
        assertFalse(workflowService.isTransicaoValida(
            StatusWorkflow.PENDENTE,
            StatusWorkflow.PENDENTE,
            TipoWorkflow.BASE_PRAZO
        ));
    }

    /**
     * Test 14: isTransicaoValida - deve retornar false para parametros nulos
     */
    @Test
    void isTransicaoValida_parametrosNulos_retornaFalse() {
        // Act & Assert
        assertFalse(workflowService.isTransicaoValida(null, StatusWorkflow.PENDENTE, TipoWorkflow.BASE_PRAZO));
        assertFalse(workflowService.isTransicaoValida(StatusWorkflow.PENDENTE, null, TipoWorkflow.BASE_PRAZO));
        assertFalse(workflowService.isTransicaoValida(StatusWorkflow.PENDENTE, StatusWorkflow.EM_ANALISE_FINANCEIRO, null));
    }

    /**
     * Test 15: getStatusPermitidos - deve retornar status permitidos
     */
    @Test
    void getStatusPermitidos_pendente_retornaStatusCorretos() {
        // Act
        Set<StatusWorkflow> permitidos = workflowService.getStatusPermitidos(
            StatusWorkflow.PENDENTE,
            TipoWorkflow.BASE_PRAZO
        );

        // Assert
        assertEquals(1, permitidos.size());
        assertTrue(permitidos.contains(StatusWorkflow.EM_ANALISE_FINANCEIRO));
    }

    /**
     * Test 16: getStatusPermitidos - FINALIZADO não tem próximos estados
     */
    @Test
    void getStatusPermitidos_finalizado_retornaVazio() {
        // Act
        Set<StatusWorkflow> permitidos = workflowService.getStatusPermitidos(
            StatusWorkflow.FINALIZADO,
            TipoWorkflow.BASE_PRAZO
        );

        // Assert
        assertTrue(permitidos.isEmpty());
    }

    /**
     * Test 17: Transição para EM_ANALISE_CLIENTE_NOVO deve iniciar análise
     */
    @Test
    void transicionar_paraEmAnaliseClienteNovo_iniciaAnalise() {
        // Arrange
        pedido.setBloqueio("80");  // CLIENTE_NOVO
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.CONSULTA_SCORE_RESTRICOES);
        analise.setDataInicio(null);

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.EM_ANALISE_CLIENTE_NOVO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.EM_ANALISE_CLIENTE_NOVO, analise.getStatusWorkflow());
        assertNotNull(analise.getDataInicio());
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 18: Transição para REANALISADO_APROVADO deve verificar alçada novamente
     */
    @Test
    void transicionar_paraReanalisadoAprovado_verificaAlcada() {
        // Arrange
        analise.setStatusWorkflow(StatusWorkflow.REANALISE_COMERCIAL_SOLICITADA);
        analise.setDataInicio(LocalDateTime.now());
        pedido.setValor(new BigDecimal("150000"));  // > threshold

        when(configuracaoRepository.findById(1L)).thenReturn(Optional.of(configuracao));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(grupoEconomicoRepository.findById(1L)).thenReturn(Optional.of(grupo));
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.REANALISADO_APROVADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.REANALISADO_APROVADO, analise.getStatusWorkflow());
        assertTrue(analise.getRequerAprovacaoGestor());
        verify(analiseRepository).save(analise);
    }

    /**
     * Test 19: Finalizar sem limite aprovado não deve atualizar grupo
     */
    @Test
    void transicionar_paraFinalizadoSemLimite_naoAtualizaGrupo() {
        // Arrange
        analise.setStatusWorkflow(StatusWorkflow.PARECER_APROVADO);
        analise.setDataInicio(LocalDateTime.now());
        analise.setLimiteAprovado(null);  // No approved limit

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.FINALIZADO, analise.getStatusWorkflow());
        assertNotNull(analise.getDataFim());
        verify(grupoEconomicoRepository, never()).save(any());
    }

    /**
     * Test 20: Finalizar com limite zero não deve atualizar grupo
     */
    @Test
    void transicionar_paraFinalizadoComLimiteZero_naoAtualizaGrupo() {
        // Arrange
        analise.setStatusWorkflow(StatusWorkflow.PARECER_REPROVADO);
        analise.setDataInicio(LocalDateTime.now());
        analise.setLimiteAprovado(BigDecimal.ZERO);

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        // Act
        workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");

        // Assert
        assertEquals(StatusWorkflow.FINALIZADO, analise.getStatusWorkflow());
        verify(grupoEconomicoRepository, never()).save(any());
    }

    // ==================== Pipeline CLIENTE_NOVO Tests ====================

    /**
     * Test 21: PENDENTE → SOLICITAR_CANCELAMENTO (terminal)
     */
    @Test
    void transicionar_clienteNovo_pendenteParaCancelamento_sucesso() {
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        workflowService.transicionar(analise, StatusWorkflow.SOLICITAR_CANCELAMENTO, "analista@teste.com");

        assertEquals(StatusWorkflow.SOLICITAR_CANCELAMENTO, analise.getStatusWorkflow());
        assertNotNull(analise.getDataFim());
    }

    /**
     * Test 22: PENDENTE → ENCAMINHADO_ANTECIPADO (terminal)
     */
    @Test
    void transicionar_clienteNovo_pendenteParaAntecipado_sucesso() {
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.PENDENTE);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        workflowService.transicionar(analise, StatusWorkflow.ENCAMINHADO_ANTECIPADO, "analista@teste.com");

        assertEquals(StatusWorkflow.ENCAMINHADO_ANTECIPADO, analise.getStatusWorkflow());
        assertNotNull(analise.getDataFim());
        assertEquals(TipoCliente.ANTECIPADO, cliente.getTipoCliente());
    }

    /**
     * Test 23: CONSULTA_PROTESTOS → VERIFICACAO_LOJA_FISICA
     */
    @Test
    void transicionar_clienteNovo_protestosParaVerifLoja_sucesso() {
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.CONSULTA_PROTESTOS);

        when(analiseRepository.save(any(Analise.class))).thenReturn(analise);

        workflowService.transicionar(analise, StatusWorkflow.VERIFICACAO_LOJA_FISICA, "analista@teste.com");

        assertEquals(StatusWorkflow.VERIFICACAO_LOJA_FISICA, analise.getStatusWorkflow());
    }

    /**
     * Test 24: SOLICITAR_CANCELAMENTO is terminal - no transitions allowed
     */
    @Test
    void transicionar_cancelamentoTerminal_lancaExcecao() {
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.SOLICITAR_CANCELAMENTO);

        assertThrows(IllegalStateException.class, () -> {
            workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");
        });
    }

    /**
     * Test 25: ENCAMINHADO_ANTECIPADO is terminal - no transitions allowed
     */
    @Test
    void transicionar_antecipadoTerminal_lancaExcecao() {
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        analise.setStatusWorkflow(StatusWorkflow.ENCAMINHADO_ANTECIPADO);

        assertThrows(IllegalStateException.class, () -> {
            workflowService.transicionar(analise, StatusWorkflow.FINALIZADO, "analista@teste.com");
        });
    }

    /**
     * Test 26: getStatusPermitidos for PENDENTE in CLIENTE_NOVO pipeline
     */
    @Test
    void getStatusPermitidos_clienteNovoPendente_retornaStatusPipeline() {
        Set<StatusWorkflow> permitidos = workflowService.getStatusPermitidos(
            StatusWorkflow.PENDENTE,
            TipoWorkflow.CLIENTE_NOVO
        );

        assertEquals(4, permitidos.size());
        assertTrue(permitidos.contains(StatusWorkflow.FAZER_CONSULTAS));
        assertTrue(permitidos.contains(StatusWorkflow.CONSULTA_PROTESTOS));
        assertTrue(permitidos.contains(StatusWorkflow.SOLICITAR_CANCELAMENTO));
        assertTrue(permitidos.contains(StatusWorkflow.ENCAMINHADO_ANTECIPADO));
    }
}
