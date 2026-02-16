package AnaliseCredito.Analise_de_Credito.infrastructure.config;

import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DataInitializer - Popula dados de teste no banco H2.
 *
 * Cria configuração padrão, grupos econômicos, clientes, pedidos e análises
 * para demonstração do sistema.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private AnaliseRepository analiseRepository;

    @Override
    public void run(String... args) throws Exception {
        // Só inicializa se o banco estiver vazio
        if (configuracaoRepository.count() > 0) {
            return;
        }

        System.out.println("=== Inicializando dados de teste ===");

        // 1. Criar Configuração Padrão
        Configuracao config = new Configuracao();
        config.setId(1L);
        config.setLimiteSimei(new BigDecimal("50000.00"));
        config.setMaxSimeisPorGrupo(3);
        config.setValorAprovacaoGestor(new BigDecimal("100000.00"));
        config.setTotalGrupoAprovacaoGestor(new BigDecimal("500000.00"));
        config.setRestricoesAprovacaoGestor(3);
        config.setScoreBaixoThreshold(400);
        configuracaoRepository.save(config);

        // 2. Criar Grupos Econômicos
        GrupoEconomico grupo1 = new GrupoEconomico();
        grupo1.setNome("Grupo Comercial ABC");
        grupo1.setLimiteAprovado(new BigDecimal("200000.00"));
        grupo1.setLimiteDisponivel(new BigDecimal("150000.00"));
        grupoEconomicoRepository.save(grupo1);

        GrupoEconomico grupo2 = new GrupoEconomico();
        grupo2.setNome("Grupo Varejo XYZ");
        grupo2.setLimiteAprovado(new BigDecimal("100000.00"));
        grupo2.setLimiteDisponivel(new BigDecimal("80000.00"));
        grupoEconomicoRepository.save(grupo2);

        // 3. Criar Clientes - BASE_PRAZO
        Cliente cliente1 = new Cliente();
        cliente1.setCnpj("12345678000191");
        cliente1.setRazaoSocial("ABC Comércio LTDA");
        cliente1.setNomeFantasia("ABC Loja");
        cliente1.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente1.setSimei(false);
        cliente1.setScoreBoaVista(750);
        cliente1.setScoreBoaVistaData(LocalDate.now());
        cliente1.setGrupoEconomico(grupo1);
        clienteRepository.save(cliente1);

        Cliente cliente2 = new Cliente();
        cliente2.setCnpj("98765432000155");
        cliente2.setRazaoSocial("XYZ Distribuidora LTDA");
        cliente2.setNomeFantasia("XYZ Varejo");
        cliente2.setTipoCliente(TipoCliente.BASE_PRAZO);
        cliente2.setSimei(false);
        cliente2.setScoreBoaVista(450);
        cliente2.setScoreBoaVistaData(LocalDate.now());
        cliente2.setGrupoEconomico(grupo2);
        clienteRepository.save(cliente2);

        // 4. Criar Clientes - CLIENTE_NOVO
        Cliente cliente3 = new Cliente();
        cliente3.setCnpj("11122233000144");
        cliente3.setRazaoSocial("Nova Empresa LTDA");
        cliente3.setNomeFantasia("Nova Loja");
        cliente3.setTipoCliente(TipoCliente.NOVO);
        cliente3.setSimei(true);
        cliente3.setScoreBoaVista(320);
        cliente3.setScoreBoaVistaData(LocalDate.now());
        cliente3.setGrupoEconomico(grupo1);
        clienteRepository.save(cliente3);

        Cliente cliente4 = new Cliente();
        cliente4.setCnpj("55566677000188");
        cliente4.setRazaoSocial("Startup Comércio LTDA");
        cliente4.setNomeFantasia("Startup");
        cliente4.setTipoCliente(TipoCliente.NOVO);
        cliente4.setSimei(false);
        cliente4.setScoreBoaVista(620);
        cliente4.setScoreBoaVistaData(LocalDate.now());
        cliente4.setGrupoEconomico(grupo2);
        clienteRepository.save(cliente4);

        // 5. Criar Pedidos e Análises - BASE_PRAZO
        // Pedido 1 - PENDENTE
        Pedido pedido1 = createPedido("PED-001", cliente1, new BigDecimal("25000.00"), "MARCA_A", "05");
        pedidoRepository.save(pedido1);
        Analise analise1 = createAnalise(pedido1, StatusWorkflow.PENDENTE);
        analiseRepository.save(analise1);
        pedido1.setAnalise(analise1);

        // Pedido 2 - EM_ANALISE_FINANCEIRO
        Pedido pedido2 = createPedido("PED-002", cliente1, new BigDecimal("35000.00"), "MARCA_B", "10");
        pedidoRepository.save(pedido2);
        Analise analise2 = createAnalise(pedido2, StatusWorkflow.EM_ANALISE_FINANCEIRO);
        analise2.setDataInicio(LocalDateTime.now().minusDays(1));
        analiseRepository.save(analise2);
        pedido2.setAnalise(analise2);

        // Pedido 3 - PARECER_APROVADO
        Pedido pedido3 = createPedido("PED-003", cliente2, new BigDecimal("20000.00"), "MARCA_A", "15");
        pedidoRepository.save(pedido3);
        Analise analise3 = createAnalise(pedido3, StatusWorkflow.PARECER_APROVADO);
        analise3.setDataInicio(LocalDateTime.now().minusDays(2));
        analiseRepository.save(analise3);
        pedido3.setAnalise(analise3);

        // 6. Criar Pedidos e Análises - CLIENTE_NOVO
        // Pedido 4 - PENDENTE
        Pedido pedido4 = createPedido("PED-004", cliente3, new BigDecimal("15000.00"), "MARCA_C", "80");
        pedidoRepository.save(pedido4);
        Analise analise4 = createAnalise(pedido4, StatusWorkflow.PENDENTE);
        analiseRepository.save(analise4);
        pedido4.setAnalise(analise4);

        // Pedido 5 - DOCUMENTACAO_SOLICITADA
        Pedido pedido5 = createPedido("PED-005", cliente4, new BigDecimal("45000.00"), "MARCA_D", "36");
        pedidoRepository.save(pedido5);
        Analise analise5 = createAnalise(pedido5, StatusWorkflow.DOCUMENTACAO_SOLICITADA);
        analise5.setDataInicio(LocalDateTime.now().minusDays(1));
        analiseRepository.save(analise5);
        pedido5.setAnalise(analise5);

        // Pedido 6 - DOCUMENTACAO_ENVIADA
        Pedido pedido6 = createPedido("PED-006", cliente3, new BigDecimal("10000.00"), "MARCA_E", "80");
        pedidoRepository.save(pedido6);
        Analise analise6 = createAnalise(pedido6, StatusWorkflow.DOCUMENTACAO_ENVIADA);
        analise6.setDataInicio(LocalDateTime.now().minusDays(3));
        analiseRepository.save(analise6);
        pedido6.setAnalise(analise6);

        System.out.println("=== Dados de teste criados com sucesso! ===");
        System.out.println("- Configuração padrão criada");
        System.out.println("- 2 grupos econômicos criados");
        System.out.println("- 4 clientes criados");
        System.out.println("- 6 pedidos e análises criados");
        System.out.println("- Acesse: http://localhost:8081/");
    }

    private Pedido createPedido(String numero, Cliente cliente, BigDecimal valor, String marca, String bloqueio) {
        Pedido pedido = new Pedido();
        pedido.setNumero(numero);
        pedido.setCliente(cliente);
        pedido.setData(LocalDate.now());
        pedido.setValor(valor);
        pedido.setMarca(marca);
        pedido.setBloqueio(bloqueio);
        pedido.setDeposito("DEP-01");
        pedido.setCondicaoPagamento("30/60 dias");
        pedido.setColecao(202601);
        pedido.calcularWorkflow(); // Calcula workflow baseado no bloqueio
        return pedido;
    }

    private Analise createAnalise(Pedido pedido, StatusWorkflow status) {
        Analise analise = new Analise();
        analise.setPedido(pedido);
        analise.setClienteId(pedido.getCliente().getId());
        analise.setGrupoEconomicoId(pedido.getCliente().getGrupoEconomico().getId());
        analise.setStatusWorkflow(status);
        analise.setScoreNoMomento(pedido.getCliente().getScoreBoaVista());
        analise.setRequerAprovacaoGestor(false);
        return analise;
    }
}
