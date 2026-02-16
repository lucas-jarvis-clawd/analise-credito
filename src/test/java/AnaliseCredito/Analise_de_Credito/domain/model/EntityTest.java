package AnaliseCredito.Analise_de_Credito.domain.model;

import AnaliseCredito.Analise_de_Credito.domain.enums.PosicaoDuplicata;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity tests - Test calculated methods in domain entities.
 *
 * Tests cover:
 * 1. Duplicata.getAtraso() edge cases
 * 2. Cliente.getTotalRestricoes()
 * 3. GrupoEconomico helper methods
 */
@SpringBootTest
public class EntityTest {

    // ========== Duplicata.getAtraso() Tests ==========

    @Test
    void duplicataGetAtraso_vencidoNaoPago_calculaCorreto() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().minusDays(10));
        duplicata.setDataPagamento(null);

        assertEquals(10, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_pagoComAtraso_calculaCorreto() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().minusDays(15));
        duplicata.setDataPagamento(LocalDate.now().minusDays(5));

        assertEquals(10, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_naoVencido_retornaZero() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().plusDays(5));
        duplicata.setDataPagamento(null);

        assertEquals(0, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_pagoNoPrazo_retornaZero() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().minusDays(5));
        duplicata.setDataPagamento(LocalDate.now().minusDays(6));

        assertEquals(0, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_vencimentoHoje_retornaZero() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now());
        duplicata.setDataPagamento(null);

        assertEquals(0, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_pagoNoVencimento_retornaZero() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().minusDays(5));
        duplicata.setDataPagamento(LocalDate.now().minusDays(5));

        assertEquals(0, duplicata.getAtraso());
    }

    @Test
    void duplicataGetAtraso_vencimentoNull_retornaZero() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(null);

        assertEquals(0, duplicata.getAtraso());
    }

    @Test
    void duplicataIsEmAtraso_comAtraso_retornaTrue() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().minusDays(10));
        duplicata.setDataPagamento(null);

        assertTrue(duplicata.isEmAtraso());
    }

    @Test
    void duplicataIsEmAtraso_semAtraso_retornaFalse() {
        Duplicata duplicata = new Duplicata();
        duplicata.setVencimento(LocalDate.now().plusDays(5));
        duplicata.setDataPagamento(null);

        assertFalse(duplicata.isEmAtraso());
    }

    @Test
    void duplicataIsPaga_paga_retornaTrue() {
        Duplicata duplicata = new Duplicata();
        duplicata.setDataPagamento(LocalDate.now().minusDays(5));

        assertTrue(duplicata.isPaga());
    }

    @Test
    void duplicataIsPaga_naoPaga_retornaFalse() {
        Duplicata duplicata = new Duplicata();
        duplicata.setDataPagamento(null);

        assertFalse(duplicata.isPaga());
    }

    // ========== Cliente.getTotalRestricoes() Tests ==========

    @Test
    void clienteGetTotalRestricoes_semRestricoes_retornaZero() {
        Cliente cliente = new Cliente();
        cliente.setPefins(new ArrayList<>());
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        assertEquals(0, cliente.getTotalRestricoes());
    }

    @Test
    void clienteGetTotalRestricoes_comRestricoes_somaTodas() {
        Cliente cliente = new Cliente();

        // Add 2 pefins
        cliente.setPefins(new ArrayList<>());
        cliente.getPefins().add(new Pefin());
        cliente.getPefins().add(new Pefin());

        // Add 1 protesto
        cliente.setProtestos(new ArrayList<>());
        cliente.getProtestos().add(new Protesto());

        // Add 3 ações judiciais
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.getAcoesJudiciais().add(new AcaoJudicial());
        cliente.getAcoesJudiciais().add(new AcaoJudicial());
        cliente.getAcoesJudiciais().add(new AcaoJudicial());

        // Add 1 cheque
        cliente.setCheques(new ArrayList<>());
        cliente.getCheques().add(new Cheque());

        assertEquals(7, cliente.getTotalRestricoes());
    }

    @Test
    void clienteGetTotalRestricoes_apenasUmTipo_retornaQuantidade() {
        Cliente cliente = new Cliente();
        cliente.setPefins(new ArrayList<>());
        cliente.setProtestos(new ArrayList<>());
        cliente.setAcoesJudiciais(new ArrayList<>());
        cliente.setCheques(new ArrayList<>());

        cliente.getProtestos().add(new Protesto());
        cliente.getProtestos().add(new Protesto());
        cliente.getProtestos().add(new Protesto());

        assertEquals(3, cliente.getTotalRestricoes());
    }

    // ========== GrupoEconomico Helper Methods Tests ==========

    @Test
    void grupoEconomicoGetCountSimeis_semClientes_retornaZero() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(new ArrayList<>());

        assertEquals(0, grupo.getCountSimeis());
    }

    @Test
    void grupoEconomicoGetCountSimeis_comSimeis_contaCorreto() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(new ArrayList<>());

        Cliente c1 = new Cliente();
        c1.setSimei(true);
        grupo.getClientes().add(c1);

        Cliente c2 = new Cliente();
        c2.setSimei(false);
        grupo.getClientes().add(c2);

        Cliente c3 = new Cliente();
        c3.setSimei(true);
        grupo.getClientes().add(c3);

        Cliente c4 = new Cliente();
        c4.setSimei(null);
        grupo.getClientes().add(c4);

        assertEquals(2, grupo.getCountSimeis());
    }

    @Test
    void grupoEconomicoIsGrupoReal_umCliente_retornaFalse() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(new ArrayList<>());
        grupo.getClientes().add(new Cliente());

        assertFalse(grupo.isGrupoReal());
    }

    @Test
    void grupoEconomicoIsGrupoReal_multiplosClientes_retornaTrue() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(new ArrayList<>());
        grupo.getClientes().add(new Cliente());
        grupo.getClientes().add(new Cliente());

        assertTrue(grupo.isGrupoReal());
    }

    @Test
    void grupoEconomicoIsGrupoReal_semClientes_retornaFalse() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(new ArrayList<>());

        assertFalse(grupo.isGrupoReal());
    }

    @Test
    void grupoEconomicoIsGrupoReal_clientesNull_retornaFalse() {
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setClientes(null);

        assertFalse(grupo.isGrupoReal());
    }
}
