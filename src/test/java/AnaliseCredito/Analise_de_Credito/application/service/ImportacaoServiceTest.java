package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import AnaliseCredito.Analise_de_Credito.presentation.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para ImportacaoService.
 *
 * Testa parsing, importação, validação e pós-processamento.
 */
@SpringBootTest
@Transactional
class ImportacaoServiceTest {

    @Autowired
    private ImportacaoService importacaoService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private DuplicataRepository duplicataRepository;

    private ResultadoImportacao resultado;

    @BeforeEach
    void setUp() {
        resultado = new ResultadoImportacao();

        // Limpar base para testes isolados
        duplicataRepository.deleteAll();
        dadosBIRepository.deleteAll();
        analiseRepository.deleteAll();
        pedidoRepository.deleteAll();
        clienteRepository.deleteAll();
        grupoEconomicoRepository.deleteAll();
    }

    // ========== TESTES DE IMPORTAÇÃO DE CLIENTES ==========

    @Test
    void testImportarClientes_Sucesso() {
        // Arrange
        List<ClienteDTO> clientes = new ArrayList<>();
        ClienteDTO dto = new ClienteDTO();
        dto.setCnpj("12345678901234");
        dto.setRazaoSocial("Empresa Teste LTDA");
        dto.setNomeFantasia("Empresa Teste");
        dto.setEstado("SP");
        dto.setTipo("BASE_PRAZO");
        dto.setSimei(false);
        dto.setGrupoEconomico("GRUPO001");
        clientes.add(dto);

        // Act
        importacaoService.importarClientes(clientes, resultado);

        // Assert
        assertEquals(1, resultado.getClientesImportados());
        assertEquals(0, resultado.getErros().size());

        Cliente cliente = clienteRepository.findByCnpj("12345678901234").orElse(null);
        assertNotNull(cliente);
        assertEquals("Empresa Teste LTDA", cliente.getRazaoSocial());
        assertEquals(TipoCliente.BASE_PRAZO, cliente.getTipoCliente());
        assertNotNull(cliente.getGrupoEconomico());
        assertEquals("GRUPO001", cliente.getGrupoEconomico().getCodigo());
    }

    @Test
    void testImportarClientes_CriaGrupoSeNaoExiste() {
        // Arrange
        List<ClienteDTO> clientes = new ArrayList<>();
        ClienteDTO dto = new ClienteDTO();
        dto.setCnpj("12345678901234");
        dto.setRazaoSocial("Empresa Teste");
        dto.setTipo("NOVO");
        dto.setGrupoEconomico("GRUPO_NOVO");
        clientes.add(dto);

        // Act
        importacaoService.importarClientes(clientes, resultado);

        // Assert
        GrupoEconomico grupo = grupoEconomicoRepository.findByCodigo("GRUPO_NOVO").orElse(null);
        assertNotNull(grupo);
        assertEquals("Grupo GRUPO_NOVO", grupo.getNome());
        assertEquals(BigDecimal.ZERO, grupo.getLimiteAprovado());
    }

    @Test
    void testImportarClientes_UsaCnpjComoGrupoSeNaoInformado() {
        // Arrange
        List<ClienteDTO> clientes = new ArrayList<>();
        ClienteDTO dto = new ClienteDTO();
        dto.setCnpj("12345678901234");
        dto.setRazaoSocial("Empresa Teste");
        dto.setTipo("BASE_PRAZO");
        dto.setGrupoEconomico(null); // Sem grupo informado
        clientes.add(dto);

        // Act
        importacaoService.importarClientes(clientes, resultado);

        // Assert
        Cliente cliente = clienteRepository.findByCnpj("12345678901234").orElse(null);
        assertNotNull(cliente);
        assertEquals("12345678901234", cliente.getGrupoEconomico().getCodigo());
    }

    @Test
    void testImportarClientes_SkipDuplicado() {
        // Arrange - Criar cliente existente
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(BigDecimal.ZERO);
        grupo.setLimiteDisponivel(BigDecimal.ZERO);
        grupoEconomicoRepository.save(grupo);

        Cliente existente = new Cliente();
        existente.setCnpj("12345678901234");
        existente.setRazaoSocial("Cliente Existente");
        existente.setTipoCliente(TipoCliente.BASE_PRAZO);
        existente.setGrupoEconomico(grupo);
        existente.setSimei(false);
        clienteRepository.save(existente);

        // Tentar importar duplicado
        List<ClienteDTO> clientes = new ArrayList<>();
        ClienteDTO dto = new ClienteDTO();
        dto.setCnpj("12345678901234");
        dto.setRazaoSocial("Cliente Novo");
        clientes.add(dto);

        // Act
        importacaoService.importarClientes(clientes, resultado);

        // Assert
        assertEquals(0, resultado.getClientesImportados());
        assertEquals(1, resultado.getAvisos().size());
        assertTrue(resultado.getAvisos().get(0).contains("já existe"));
    }

    @Test
    void testImportarClientes_ValidacaoCnpjObrigatorio() {
        // Arrange
        List<ClienteDTO> clientes = new ArrayList<>();
        ClienteDTO dto = new ClienteDTO();
        dto.setCnpj(null); // CNPJ ausente
        dto.setRazaoSocial("Empresa Teste");
        clientes.add(dto);

        // Act
        importacaoService.importarClientes(clientes, resultado);

        // Assert
        assertEquals(0, resultado.getClientesImportados());
        assertEquals(1, resultado.getErros().size());
        assertTrue(resultado.getErros().get(0).contains("sem CNPJ"));
    }

    // ========== TESTES DE IMPORTAÇÃO DE PEDIDOS ==========

    @Test
    void testImportarPedidos_Sucesso() {
        // Arrange - Criar cliente primeiro
        GrupoEconomico grupo = criarGrupoTeste();
        Cliente cliente = criarClienteTeste(grupo);

        List<PedidoDTO> pedidos = new ArrayList<>();
        PedidoDTO dto = new PedidoDTO();
        dto.setNumero("PED001");
        dto.setData(LocalDate.now());
        dto.setValor(new BigDecimal("1000.00"));
        dto.setCnpjCliente(cliente.getCnpj());
        dto.setBloqueio("80"); // CLIENTE_NOVO
        dto.setColecao(202601);
        pedidos.add(dto);

        // Act
        importacaoService.importarPedidos(pedidos, resultado);

        // Assert
        assertEquals(1, resultado.getPedidosImportados());
        assertEquals(0, resultado.getErros().size());

        List<Pedido> pedidosSalvos = pedidoRepository.findAll();
        assertEquals(1, pedidosSalvos.size());
        Pedido pedido = pedidosSalvos.get(0);
        assertEquals("PED001", pedido.getNumero());
        assertEquals(TipoWorkflow.CLIENTE_NOVO, pedido.getWorkflow());
    }

    @Test
    void testImportarPedidos_DefineWorkflowCorretamente() {
        // Arrange
        GrupoEconomico grupo = criarGrupoTeste();
        Cliente cliente = criarClienteTeste(grupo);

        List<PedidoDTO> pedidos = new ArrayList<>();

        // Pedido com bloqueio 80 = CLIENTE_NOVO
        PedidoDTO dto1 = new PedidoDTO();
        dto1.setNumero("PED001");
        dto1.setCnpjCliente(cliente.getCnpj());
        dto1.setBloqueio("80");
        dto1.setData(LocalDate.now());
        dto1.setValor(BigDecimal.ZERO);
        pedidos.add(dto1);

        // Pedido com bloqueio 36 = CLIENTE_NOVO
        PedidoDTO dto2 = new PedidoDTO();
        dto2.setNumero("PED002");
        dto2.setCnpjCliente(cliente.getCnpj());
        dto2.setBloqueio("36");
        dto2.setData(LocalDate.now());
        dto2.setValor(BigDecimal.ZERO);
        pedidos.add(dto2);

        // Pedido com outro bloqueio = BASE_PRAZO
        PedidoDTO dto3 = new PedidoDTO();
        dto3.setNumero("PED003");
        dto3.setCnpjCliente(cliente.getCnpj());
        dto3.setBloqueio("99");
        dto3.setData(LocalDate.now());
        dto3.setValor(BigDecimal.ZERO);
        pedidos.add(dto3);

        // Act
        importacaoService.importarPedidos(pedidos, resultado);

        // Assert
        assertEquals(3, resultado.getPedidosImportados());

        Pedido p1 = pedidoRepository.findAll().stream()
                .filter(p -> "PED001".equals(p.getNumero())).findFirst().orElse(null);
        assertEquals(TipoWorkflow.CLIENTE_NOVO, p1.getWorkflow());

        Pedido p2 = pedidoRepository.findAll().stream()
                .filter(p -> "PED002".equals(p.getNumero())).findFirst().orElse(null);
        assertEquals(TipoWorkflow.CLIENTE_NOVO, p2.getWorkflow());

        Pedido p3 = pedidoRepository.findAll().stream()
                .filter(p -> "PED003".equals(p.getNumero())).findFirst().orElse(null);
        assertEquals(TipoWorkflow.BASE_PRAZO, p3.getWorkflow());
    }

    @Test
    void testImportarPedidos_CriaAnalisePendente() {
        // Arrange
        GrupoEconomico grupo = criarGrupoTeste();
        Cliente cliente = criarClienteTeste(grupo);

        List<PedidoDTO> pedidos = new ArrayList<>();
        PedidoDTO dto = new PedidoDTO();
        dto.setNumero("PED001");
        dto.setCnpjCliente(cliente.getCnpj());
        dto.setData(LocalDate.now());
        dto.setValor(BigDecimal.ZERO);
        pedidos.add(dto);

        // Act
        importacaoService.importarPedidos(pedidos, resultado);

        // Assert
        List<Analise> analises = analiseRepository.findAll();
        assertEquals(1, analises.size());

        Analise analise = analises.get(0);
        assertEquals(StatusWorkflow.PENDENTE, analise.getStatusWorkflow());
        assertEquals(cliente.getId(), analise.getClienteId());
        assertEquals(grupo.getId(), analise.getGrupoEconomicoId());
        assertNotNull(analise.getDataInicio());
    }

    @Test
    void testImportarPedidos_ClienteNaoEncontrado() {
        // Arrange
        List<PedidoDTO> pedidos = new ArrayList<>();
        PedidoDTO dto = new PedidoDTO();
        dto.setNumero("PED001");
        dto.setCnpjCliente("99999999999999"); // Cliente inexistente
        dto.setData(LocalDate.now());
        dto.setValor(BigDecimal.ZERO);
        pedidos.add(dto);

        // Act
        importacaoService.importarPedidos(pedidos, resultado);

        // Assert
        assertEquals(0, resultado.getPedidosImportados());
        assertEquals(1, resultado.getErros().size());
        assertTrue(resultado.getErros().get(0).contains("não encontrado"));
    }

    // ========== TESTES DE IMPORTAÇÃO DE DADOS BI ==========

    @Test
    void testImportarDadosBI_Sucesso() {
        // Arrange
        GrupoEconomico grupo = criarGrupoTeste();

        List<DadosBIDTO> dadosBI = new ArrayList<>();
        DadosBIDTO dto = new DadosBIDTO();
        dto.setGrupoEconomico(grupo.getCodigo());
        dto.setColecao(202601);
        dto.setValorVencido(new BigDecimal("500.00"));
        dto.setCredito(new BigDecimal("10000.00"));
        dto.setScore(750);
        dto.setAtrasoMedio(new BigDecimal("5.50"));
        dadosBI.add(dto);

        // Act
        importacaoService.importarDadosBI(dadosBI, resultado);

        // Assert
        assertEquals(1, resultado.getDadosBIImportados());
        assertEquals(0, resultado.getErros().size());

        List<DadosBI> dadosSalvos = dadosBIRepository.findAll();
        assertEquals(1, dadosSalvos.size());

        DadosBI dados = dadosSalvos.get(0);
        assertEquals(202601, dados.getColecao());
        assertEquals(750, dados.getScore());
        assertNotNull(dados.getDataImportacao());
    }

    @Test
    void testImportarDadosBI_GrupoNaoEncontrado() {
        // Arrange
        List<DadosBIDTO> dadosBI = new ArrayList<>();
        DadosBIDTO dto = new DadosBIDTO();
        dto.setGrupoEconomico("GRUPO_INEXISTENTE");
        dto.setColecao(202601);
        dadosBI.add(dto);

        // Act
        importacaoService.importarDadosBI(dadosBI, resultado);

        // Assert
        assertEquals(0, resultado.getDadosBIImportados());
        assertEquals(1, resultado.getErros().size());
        assertTrue(resultado.getErros().get(0).contains("não encontrado"));
    }

    // ========== TESTES DE IMPORTAÇÃO DE DUPLICATAS ==========

    @Test
    void testImportarDuplicatas_Sucesso() {
        // Arrange
        GrupoEconomico grupo = criarGrupoTeste();
        Cliente cliente = criarClienteTeste(grupo);

        List<DuplicataDTO> duplicatas = new ArrayList<>();
        DuplicataDTO dto = new DuplicataDTO();
        dto.setCnpj(cliente.getCnpj());
        dto.setPosicao("CARTEIRA");
        dto.setPortador("Banco Teste");
        dto.setVencimento(LocalDate.now().minusDays(30));
        dto.setValor(new BigDecimal("1500.00"));
        dto.setSaldo(new BigDecimal("1500.00"));
        duplicatas.add(dto);

        // Act
        importacaoService.importarDuplicatas(duplicatas, resultado);

        // Assert
        assertEquals(1, resultado.getDuplicatasImportadas());
        assertEquals(0, resultado.getErros().size());

        List<Duplicata> duplicatasSalvas = duplicataRepository.findAll();
        assertEquals(1, duplicatasSalvas.size());

        Duplicata duplicata = duplicatasSalvas.get(0);
        assertEquals(cliente.getId(), duplicata.getCliente().getId());
        assertEquals(new BigDecimal("1500.00"), duplicata.getValor());
    }

    // ========== TESTES DE RESULTADO IMPORTAÇÃO ==========

    @Test
    void testResultadoImportacao_FinalizarComStatus() {
        // Sucesso total
        ResultadoImportacao r1 = new ResultadoImportacao();
        r1.incrementarClientes();
        r1.finalizarComStatus();
        assertEquals("SUCESSO", r1.getStatus());

        // Sucesso parcial
        ResultadoImportacao r2 = new ResultadoImportacao();
        r2.incrementarClientes();
        r2.addErro("Erro teste");
        r2.finalizarComStatus();
        assertEquals("SUCESSO_PARCIAL", r2.getStatus());

        // Erro total
        ResultadoImportacao r3 = new ResultadoImportacao();
        r3.addErro("Erro teste");
        r3.finalizarComStatus();
        assertEquals("ERRO", r3.getStatus());
    }

    @Test
    void testResultadoImportacao_Contadores() {
        ResultadoImportacao r = new ResultadoImportacao();
        r.incrementarClientes();
        r.incrementarClientes();
        r.incrementarPedidos();
        r.incrementarDadosBI();
        r.incrementarDuplicatas();

        assertEquals(2, r.getClientesImportados());
        assertEquals(1, r.getPedidosImportados());
        assertEquals(1, r.getDadosBIImportados());
        assertEquals(1, r.getDuplicatasImportadas());
        assertEquals(5, r.getTotalImportado());
    }

    // ========== HELPERS ==========

    private GrupoEconomico criarGrupoTeste() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO_TESTE");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(new BigDecimal("50000.00"));
        grupo.setLimiteDisponivel(new BigDecimal("50000.00"));
        return grupoEconomicoRepository.save(grupo);
    }

    private Cliente criarClienteTeste(GrupoEconomico grupo) {
        Cliente cliente = new Cliente();
        cliente.setCnpj("12345678901234");
        cliente.setRazaoSocial("Cliente Teste LTDA");
        cliente.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente.setGrupoEconomico(grupo);
        cliente.setSimei(false);
        return clienteRepository.save(cliente);
    }
}
