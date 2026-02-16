package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.ClienteRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ParecerService.
 *
 * Tests cover:
 * 1. Parecer generation ONLY for CLIENTE_NOVO workflow
 * 2. Returns null for BASE_PRAZO workflow
 * 3. Correct formatting of all fields
 * 4. Helper methods (extrairTipo, formatarCredito)
 * 5. Null field handling
 */
@ExtendWith(MockitoExtension.class)
class ParecerServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ParecerService parecerService;

    private Analise analise;
    private Pedido pedido;
    private Cliente cliente;
    private GrupoEconomico grupo;

    @BeforeEach
    void setUp() {
        // Setup grupo economico
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");

        // Setup cliente
        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setCnpj("12345678000190");
        cliente.setRazaoSocial("EMPRESA TESTE LTDA");
        cliente.setNomeFantasia("Empresa Teste");
        cliente.setTipoCliente(TipoCliente.NOVO);
        cliente.setSimei(true);
        cliente.setDataFundacao(LocalDate.of(2018, 5, 15));
        cliente.setScoreBoaVista(720);
        cliente.setGrupoEconomico(grupo);

        // Setup socios
        Socio socio1 = new Socio();
        socio1.setId(1L);
        socio1.setCpf("12345678901");
        socio1.setNome("João Silva");
        socio1.setCliente(cliente);

        Socio socio2 = new Socio();
        socio2.setId(2L);
        socio2.setCpf("98765432109");
        socio2.setNome("Maria Santos");
        socio2.setCliente(cliente);

        cliente.setSocios(Arrays.asList(socio1, socio2));

        // Setup participacoes
        Participacao participacao = new Participacao();
        participacao.setId(1L);
        participacao.setEmpresaCnpj("98765432000190");
        participacao.setEmpresaNome("Outra Empresa LTDA");
        participacao.setCliente(cliente);

        cliente.setParticipacoes(new ArrayList<>(Arrays.asList(participacao)));

        // Setup restricoes
        Pefin pefin1 = new Pefin();
        pefin1.setId(1L);
        pefin1.setCliente(cliente);

        Pefin pefin2 = new Pefin();
        pefin2.setId(2L);
        pefin2.setCliente(cliente);

        cliente.setPefins(Arrays.asList(pefin1, pefin2));
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        // Setup pedido
        pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumero("PED001");
        pedido.setData(LocalDate.now());
        pedido.setValor(new BigDecimal("10000"));
        pedido.setBloqueio("80");
        pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
        pedido.setCliente(cliente);

        // Setup analise
        analise = new Analise();
        analise.setId(1L);
        analise.setPedido(pedido);
        analise.setClienteId(1L);
        analise.setGrupoEconomicoId(1L);
        analise.setDecisao("APROVADO");
        analise.setLimiteSugerido(new BigDecimal("45000"));
        analise.setDataInicio(LocalDateTime.now().minusDays(1));
        analise.setDataFim(LocalDateTime.of(2026, 2, 15, 14, 30));
    }

    /**
     * Test 1: Workflow CLIENTE_NOVO deve gerar texto formatado completo
     *
     * Setup: analise com workflow=CLIENTE_NOVO, todos dados preenchidos
     * Expected: string no formato correto com todos os campos
     */
    @Test
    void gerarParecerCRM_workflowNovo_geraTextoFormatado() {
        // Arrange
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.startsWith("[APROVADO] 15/02/2026"));
        assertTrue(parecer.contains("LTDA"));
        assertTrue(parecer.contains("05/2018"));
        assertTrue(parecer.contains("SIM"));
        assertTrue(parecer.contains("2 -")); // 2 restrições
        assertTrue(parecer.contains("R$45K"));
        assertTrue(parecer.contains("720"));
        assertTrue(parecer.contains("2 SÓCIOS"));
        assertTrue(parecer.contains("1 PART"));

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 2: Workflow BASE_PRAZO não deve gerar texto
     *
     * Setup: analise com workflow=BASE_PRAZO
     * Expected: parecer = null
     */
    @Test
    void gerarParecerCRM_workflowBase_naoGeraTexto() {
        // Arrange
        pedido.setWorkflow(TipoWorkflow.BASE_PRAZO);
        pedido.setBloqueio("99");

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNull(parecer);
        verify(clienteRepository, never()).findById(anyLong());
    }

    /**
     * Test 3: Extrair tipo LTDA da razão social
     */
    @Test
    void extrairTipo_razaoSocialComLTDA_retornaLTDA() {
        // Act
        String tipo = parecerService.extrairTipo("EMPRESA TESTE LTDA");

        // Assert
        assertEquals("LTDA", tipo);
    }

    /**
     * Test 4: Extrair tipo MEI da razão social
     */
    @Test
    void extrairTipo_razaoSocialComMEI_retornaMEI() {
        // Act
        String tipo = parecerService.extrairTipo("JOAO SILVA MEI");

        // Assert
        assertEquals("MEI", tipo);
    }

    /**
     * Test 5: Extrair tipo S/A da razão social
     */
    @Test
    void extrairTipo_razaoSocialComSA_retornaSA() {
        // Act
        String tipo1 = parecerService.extrairTipo("EMPRESA TESTE S/A");
        String tipo2 = parecerService.extrairTipo("EMPRESA TESTE SA");

        // Assert
        assertEquals("S/A", tipo1);
        assertEquals("S/A", tipo2);
    }

    /**
     * Test 6: Extrair tipo EIRELI da razão social
     */
    @Test
    void extrairTipo_razaoSocialComEIRELI_retornaEIRELI() {
        // Act
        String tipo = parecerService.extrairTipo("EMPRESA TESTE EIRELI");

        // Assert
        assertEquals("EIRELI", tipo);
    }

    /**
     * Test 7: Razão social sem tipo conhecido retorna OUTROS
     */
    @Test
    void extrairTipo_razaoSocialSemTipo_retornaOutros() {
        // Act
        String tipo = parecerService.extrairTipo("EMPRESA QUALQUER");

        // Assert
        assertEquals("OUTROS", tipo);
    }

    /**
     * Test 8: Razão social null retorna N/D
     */
    @Test
    void extrairTipo_razaoSocialNull_retornaND() {
        // Act
        String tipo = parecerService.extrairTipo(null);

        // Assert
        assertEquals("N/D", tipo);
    }

    /**
     * Test 9: Formatar crédito em milhares com K
     *
     * Setup: valor = 45000
     * Expected: "R$45K"
     */
    @Test
    void formatarCredito_milhar_retornaK() {
        // Act
        String credito = parecerService.formatarCredito(new BigDecimal("45000"));

        // Assert
        assertEquals("R$45K", credito);
    }

    /**
     * Test 10: Formatar crédito em milhões com M
     *
     * Setup: valor = 1500000
     * Expected: "R$1.5M"
     */
    @Test
    void formatarCredito_milhao_retornaM() {
        // Act
        String credito = parecerService.formatarCredito(new BigDecimal("1500000"));

        // Assert
        assertEquals("R$1.5M", credito);
    }

    /**
     * Test 11: Formatar crédito menor que 1000
     */
    @Test
    void formatarCredito_menorQueMil_retornaValorInteiro() {
        // Act
        String credito = parecerService.formatarCredito(new BigDecimal("750"));

        // Assert
        assertEquals("R$750", credito);
    }

    /**
     * Test 12: Formatar crédito zero retorna N/D
     */
    @Test
    void formatarCredito_zero_retornaND() {
        // Act
        String credito = parecerService.formatarCredito(BigDecimal.ZERO);

        // Assert
        assertEquals("N/D", credito);
    }

    /**
     * Test 13: Formatar crédito null retorna N/D
     */
    @Test
    void formatarCredito_null_retornaND() {
        // Act
        String credito = parecerService.formatarCredito(null);

        // Assert
        assertEquals("N/D", credito);
    }

    /**
     * Test 14: Parecer com campos null deve tratar nulls gracefully
     *
     * Setup: cliente com campos opcionais null
     * Expected: parecer com "N/D" onde apropriado
     */
    @Test
    void gerarParecerCRM_camposNull_trataNulls() {
        // Arrange
        cliente.setDataFundacao(null);
        cliente.setScoreBoaVista(null);
        cliente.setSimei(null);
        analise.setDecisao(null);
        analise.setLimiteSugerido(null);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.startsWith("[EM ANÁLISE]")); // decisao null
        assertTrue(parecer.contains("N/D")); // dataFundacao null
        assertTrue(parecer.contains("NÃO")); // simei null tratado como false
        assertTrue(parecer.contains("N/D")); // limiteSugerido null
        assertTrue(parecer.contains("N/D")); // scoreBoaVista null

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 15: Parecer usa data atual quando dataFim é null
     */
    @Test
    void gerarParecerCRM_dataFimNull_usaDataAtual() {
        // Arrange
        analise.setDataFim(null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        LocalDate hoje = LocalDate.now();
        String dataEsperada = hoje.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertTrue(parecer.contains(dataEsperada));

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 16: Cliente não encontrado lança exceção
     */
    @Test
    void gerarParecerCRM_clienteNaoEncontrado_lancaExcecao() {
        // Arrange
        when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parecerService.gerarParecerCRM(analise);
        });

        assertEquals("Cliente não encontrado", exception.getMessage());
        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 17: Parecer com SIMEI false
     */
    @Test
    void gerarParecerCRM_simeiFalse_mostraNao() {
        // Arrange
        cliente.setSimei(false);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.contains("NÃO"));

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 18: Parecer com zero restrições
     */
    @Test
    void gerarParecerCRM_zeroRestricoes_mostraZero() {
        // Arrange
        cliente.setPefins(new ArrayList<>());
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.contains("0 -")); // 0 restrições

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 19: Parecer com zero sócios
     */
    @Test
    void gerarParecerCRM_zeroSocios_mostraZero() {
        // Arrange
        cliente.setSocios(new ArrayList<>());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.contains("0 SÓCIOS"));

        verify(clienteRepository).findById(1L);
    }

    /**
     * Test 20: Parecer com múltiplas participações
     */
    @Test
    void gerarParecerCRM_multiplasParts_mostraContagem() {
        // Arrange
        Participacao part2 = new Participacao();
        part2.setId(2L);
        part2.setEmpresaCnpj("11111111000190");
        part2.setEmpresaNome("Terceira Empresa");
        part2.setCliente(cliente);

        Participacao part3 = new Participacao();
        part3.setId(3L);
        part3.setEmpresaCnpj("22222222000190");
        part3.setEmpresaNome("Quarta Empresa");
        part3.setCliente(cliente);

        cliente.getParticipacoes().add(part2);
        cliente.getParticipacoes().add(part3);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        String parecer = parecerService.gerarParecerCRM(analise);

        // Assert
        assertNotNull(parecer);
        assertTrue(parecer.contains("3 PART")); // 3 participações

        verify(clienteRepository).findById(1L);
    }
}
