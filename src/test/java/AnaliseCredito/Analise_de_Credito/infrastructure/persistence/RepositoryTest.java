package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.DadosBI;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests - Custom query tests.
 *
 * Tests cover:
 * 1. ClienteRepository.findByCnpj()
 * 2. DadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc()
 */
@SpringBootTest
@Transactional
public class RepositoryTest {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    // ========== ClienteRepository Tests ==========

    @Test
    void clienteRepository_findByCnpj_encontraCliente() {
        // Create grupo
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(BigDecimal.valueOf(100000));
        grupo.setLimiteDisponivel(BigDecimal.valueOf(100000));
        grupo = grupoEconomicoRepository.save(grupo);

        // Create cliente
        Cliente cliente = new Cliente();
        cliente.setCnpj("12345678901234");
        cliente.setRazaoSocial("Empresa Teste LTDA");
        cliente.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente.setGrupoEconomico(grupo);
        cliente.setSimei(false);
        cliente = clienteRepository.save(cliente);

        // Test findByCnpj
        Optional<Cliente> encontrado = clienteRepository.findByCnpj("12345678901234");

        assertTrue(encontrado.isPresent());
        assertEquals("Empresa Teste LTDA", encontrado.get().getRazaoSocial());
    }

    @Test
    void clienteRepository_findByCnpj_cnpjInexistente_retornaEmpty() {
        Optional<Cliente> encontrado = clienteRepository.findByCnpj("99999999999999");

        assertFalse(encontrado.isPresent());
    }

    @Test
    void clienteRepository_findByGrupoEconomicoId_encontraClientes() {
        // Create grupo
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO002");
        grupo.setNome("Grupo Economico Teste");
        grupo.setLimiteAprovado(BigDecimal.valueOf(200000));
        grupo.setLimiteDisponivel(BigDecimal.valueOf(200000));
        grupo = grupoEconomicoRepository.save(grupo);

        // Create multiple clientes
        Cliente cliente1 = new Cliente();
        cliente1.setCnpj("11111111111111");
        cliente1.setRazaoSocial("Empresa 1");
        cliente1.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente1.setGrupoEconomico(grupo);
        cliente1.setSimei(false);
        clienteRepository.save(cliente1);

        Cliente cliente2 = new Cliente();
        cliente2.setCnpj("22222222222222");
        cliente2.setRazaoSocial("Empresa 2");
        cliente2.setTipoCliente(TipoCliente.NOVO);
        cliente2.setGrupoEconomico(grupo);
        cliente2.setSimei(false);
        clienteRepository.save(cliente2);

        // Test findByGrupoEconomicoId
        List<Cliente> clientes = clienteRepository.findByGrupoEconomicoId(grupo.getId());

        assertEquals(2, clientes.size());
    }

    // ========== DadosBIRepository Tests ==========

    @Test
    void dadosBIRepository_findByGrupoEconomicoIdOrderByColecaoDesc_ordenaCorreto() {
        // Create grupo
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO003");
        grupo.setNome("Grupo BI Teste");
        grupo.setLimiteAprovado(BigDecimal.valueOf(150000));
        grupo.setLimiteDisponivel(BigDecimal.valueOf(150000));
        grupo = grupoEconomicoRepository.save(grupo);

        // Create DadosBI with different collections
        DadosBI dados1 = new DadosBI();
        dados1.setGrupoEconomico(grupo);
        dados1.setColecao(202501);
        dados1.setCredito(BigDecimal.valueOf(50000));
        dados1.setValorVencido(BigDecimal.valueOf(30000));
        dados1.setAtrasoMedio(BigDecimal.ZERO);
        dados1.setDataImportacao(LocalDateTime.now());
        dadosBIRepository.save(dados1);

        DadosBI dados2 = new DadosBI();
        dados2.setGrupoEconomico(grupo);
        dados2.setColecao(202503);
        dados2.setCredito(BigDecimal.valueOf(60000));
        dados2.setValorVencido(BigDecimal.valueOf(35000));
        dados2.setAtrasoMedio(BigDecimal.valueOf(5));
        dados2.setDataImportacao(LocalDateTime.now());
        dadosBIRepository.save(dados2);

        DadosBI dados3 = new DadosBI();
        dados3.setGrupoEconomico(grupo);
        dados3.setColecao(202502);
        dados3.setCredito(BigDecimal.valueOf(55000));
        dados3.setValorVencido(BigDecimal.valueOf(32000));
        dados3.setAtrasoMedio(BigDecimal.valueOf(2));
        dados3.setDataImportacao(LocalDateTime.now());
        dadosBIRepository.save(dados3);

        // Test ordering - should be DESC (most recent first)
        List<DadosBI> dadosBI = dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(grupo.getId());

        assertEquals(3, dadosBI.size());
        assertEquals(202503, dadosBI.get(0).getColecao()); // Most recent first
        assertEquals(202502, dadosBI.get(1).getColecao());
        assertEquals(202501, dadosBI.get(2).getColecao()); // Oldest last
    }

    @Test
    void dadosBIRepository_findByGrupoEconomicoIdOrderByColecaoDesc_grupoSemDados_retornaListaVazia() {
        // Create grupo without BI data
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO004");
        grupo.setNome("Grupo Sem Dados");
        grupo.setLimiteAprovado(BigDecimal.valueOf(100000));
        grupo.setLimiteDisponivel(BigDecimal.valueOf(100000));
        grupo = grupoEconomicoRepository.save(grupo);

        List<DadosBI> dadosBI = dadosBIRepository.findByGrupoEconomicoIdOrderByColecaoDesc(grupo.getId());

        assertTrue(dadosBI.isEmpty());
    }

    @Test
    void dadosBIRepository_findByGrupoEconomicoId_encontraDados() {
        // Create grupo
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("GRUPO005");
        grupo.setNome("Grupo Multiplos Dados");
        grupo.setLimiteAprovado(BigDecimal.valueOf(150000));
        grupo.setLimiteDisponivel(BigDecimal.valueOf(150000));
        grupo = grupoEconomicoRepository.save(grupo);

        // Create multiple DadosBI
        for (int i = 1; i <= 3; i++) {
            DadosBI dados = new DadosBI();
            dados.setGrupoEconomico(grupo);
            dados.setColecao(202500 + i);
            dados.setCredito(BigDecimal.valueOf(50000));
            dados.setValorVencido(BigDecimal.valueOf(30000));
            dados.setAtrasoMedio(BigDecimal.ZERO);
            dados.setDataImportacao(LocalDateTime.now());
            dadosBIRepository.save(dados);
        }

        List<DadosBI> dadosBI = dadosBIRepository.findByGrupoEconomicoId(grupo.getId());

        assertEquals(3, dadosBI.size());
    }
}
