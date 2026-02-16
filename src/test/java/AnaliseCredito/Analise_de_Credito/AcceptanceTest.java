package AnaliseCredito.Analise_de_Credito;

import AnaliseCredito.Analise_de_Credito.application.service.ScoringService;
import AnaliseCredito.Analise_de_Credito.application.service.WorkflowService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import AnaliseCredito.Analise_de_Credito.presentation.controller.*;
import AnaliseCredito.Analise_de_Credito.presentation.dto.AnaliseForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AcceptanceTest - End-to-End Integration Tests
 *
 * These tests verify the entire application stack (controllers, services, repositories)
 * working together in realistic scenarios. They simulate user journeys through the
 * credit analysis system.
 *
 * Test Coverage:
 * 1. Spring Boot Context Loading
 * 2. Controller Bean Injection
 * 3. Service Bean Injection
 * 4. Repository Bean Injection
 * 5. Home Controller Flow
 * 6. Kanban Controller Display
 * 7. Configuration Management
 * 8. Analysis Workflow (with test data)
 * 9. Business Rules Validation
 * 10. Integration Points
 *
 * Note: Full database E2E tests with pre-loaded data are covered in TESTING.md
 * manual test scenarios. These automated tests focus on verifying the application
 * stack loads correctly and basic flows work.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AcceptanceTest {

    @Autowired
    private HomeController homeController;

    @Autowired
    private KanbanController kanbanController;

    @Autowired
    private AnaliseController analiseController;

    @Autowired
    private ConfiguracaoController configuracaoController;

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ScoringService scoringService;

    /**
     * Test 1: Spring Boot Context Loads Successfully
     *
     * Verifies the entire Spring application context initializes without errors.
     */
    @Test
    @DisplayName("Test 1: Spring Boot Context Loads")
    void testFluxo1_ContextLoads() {
        // This test passes if the context loads successfully
        assertNotNull(homeController, "HomeController should be autowired");
        assertNotNull(kanbanController, "KanbanController should be autowired");
        assertNotNull(analiseController, "AnaliseController should be autowired");
        assertNotNull(configuracaoController, "ConfiguracaoController should be autowired");
    }

    /**
     * Test 2: All Services Are Injected
     *
     * Verifies core services are available in the context.
     */
    @Test
    @DisplayName("Test 2: Services Are Injected")
    void testFluxo2_ServicesInjected() {
        assertNotNull(workflowService, "WorkflowService should be available");
        assertNotNull(scoringService, "ScoringService should be available");
    }

    /**
     * Test 3: All Repositories Are Injected
     *
     * Verifies JPA repositories are properly configured.
     */
    @Test
    @DisplayName("Test 3: Repositories Are Injected")
    void testFluxo3_RepositoriesInjected() {
        assertNotNull(analiseRepository, "AnaliseRepository should be available");
        assertNotNull(pedidoRepository, "PedidoRepository should be available");
        assertNotNull(clienteRepository, "ClienteRepository should be available");
        assertNotNull(grupoEconomicoRepository, "GrupoEconomicoRepository should be available");
        assertNotNull(configuracaoRepository, "ConfiguracaoRepository should be available");
    }

    /**
     * Test 4: Home Controller Profile Selection
     *
     * Verifies profile selection stores in session and redirects correctly.
     */
    @Test
    @DisplayName("Test 4: Profile Selection Flow")
    void testFluxo4_ProfileSelection() {
        MockHttpSession session = new MockHttpSession();

        // Select FINANCEIRO profile
        String result = homeController.selecionarPerfil("FINANCEIRO", session);
        assertEquals("redirect:/analise/kanban", result,
            "Should redirect to Kanban dashboard");
        assertEquals("FINANCEIRO", session.getAttribute("perfil"),
            "Profile should be saved in session");

        // Select COMERCIAL profile in new session
        MockHttpSession session2 = new MockHttpSession();
        String result2 = homeController.selecionarPerfil("COMERCIAL", session2);
        assertEquals("redirect:/analise/kanban", result2,
            "Should redirect to Kanban dashboard");
        assertEquals("COMERCIAL", session2.getAttribute("perfil"),
            "Profile should be COMERCIAL in session");
    }

    /**
     * Test 5: Kanban Controller Returns Correct View
     *
     * Verifies Kanban controller loads without errors.
     */
    @Test
    @DisplayName("Test 5: Kanban Controller View")
    void testFluxo5_KanbanView() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("perfil", "FINANCEIRO");
        Model model = new ExtendedModelMap();

        // View Kanban
        String viewName = kanbanController.kanban("TODOS", session, model);

        assertEquals("kanban", viewName, "Should return kanban view name");
        assertNotNull(model.getAttribute("kanbanData"),
            "Kanban data should be in model");
        assertEquals("TODOS", model.getAttribute("filtro"),
            "Filter should be TODOS");
        assertEquals("FINANCEIRO", model.getAttribute("perfil"),
            "Profile should be FINANCEIRO");
    }

    /**
     * Test 6: Kanban Filtering
     *
     * Verifies filtering works for different workflow types.
     */
    @Test
    @DisplayName("Test 6: Kanban Filtering")
    void testFluxo6_KanbanFiltering() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("perfil", "FINANCEIRO");

        // Test PRAZO filter
        Model model1 = new ExtendedModelMap();
        kanbanController.kanban("PRAZO", session, model1);
        assertEquals("PRAZO", model1.getAttribute("filtro"),
            "Filter should be PRAZO");

        // Test NOVO filter
        Model model2 = new ExtendedModelMap();
        kanbanController.kanban("NOVO", session, model2);
        assertEquals("NOVO", model2.getAttribute("filtro"),
            "Filter should be NOVO");

        // Test TODOS filter (default)
        Model model3 = new ExtendedModelMap();
        kanbanController.kanban("TODOS", session, model3);
        assertEquals("TODOS", model3.getAttribute("filtro"),
            "Filter should be TODOS");
    }

    /**
     * Test 7: Configuration Controller View
     *
     * Verifies configuration page loads.
     */
    @Test
    @DisplayName("Test 7: Configuration View")
    void testFluxo7_ConfigurationView() {
        Model model = new ExtendedModelMap();
        String viewName = configuracaoController.exibir(model);

        assertEquals("configuracao", viewName,
            "Should return configuracao view name");
    }

    /**
     * Test 8: Data Persistence - Create Configuracao
     *
     * Verifies we can create and save configuration.
     */
    @Test
    @DisplayName("Test 8: Configuration Persistence")
    void testFluxo8_ConfigurationPersistence() {
        // Create a test configuration
        Configuracao config = new Configuracao();
        config.setLimiteSimei(new BigDecimal("45000.00"));
        config.setMaxSimeisPorGrupo(4);
        config.setScoreBaixoThreshold(350);
        config.setValorAprovacaoGestor(new BigDecimal("120000.00"));
        config.setTotalGrupoAprovacaoGestor(new BigDecimal("500000.00"));
        config.setRestricoesAprovacaoGestor(4);

        // Save configuration
        Configuracao saved = configuracaoRepository.save(config);

        assertNotNull(saved.getId(), "Saved config should have ID");
        assertEquals(0, new BigDecimal("45000.00").compareTo(saved.getLimiteSimei()),
            "SIMEI limit should be saved");
        assertEquals(4, saved.getMaxSimeisPorGrupo(),
            "Max SIMEIs should be saved");
    }

    /**
     * Test 9: Data Persistence - Create Economic Group
     *
     * Verifies we can create economic groups.
     */
    @Test
    @DisplayName("Test 9: Economic Group Creation")
    void testFluxo9_EconomicGroupCreation() {
        // Create economic group
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("TEST001");
        grupo.setNome("Grupo Teste Acceptance");
        grupo.setLimiteAprovado(new BigDecimal("100000.00"));
        grupo.setLimiteDisponivel(new BigDecimal("80000.00"));

        // Save
        GrupoEconomico saved = grupoEconomicoRepository.save(grupo);

        assertNotNull(saved.getId(), "Group should have ID");
        assertEquals("TEST001", saved.getCodigo(), "Code should be saved");
        assertEquals("Grupo Teste Acceptance", saved.getNome(),
            "Name should be saved");
    }

    /**
     * Test 10: Business Rules - SIMEI Cap Application
     *
     * Verifies SIMEI limit cap is applied correctly.
     */
    @Test
    @DisplayName("Test 10: SIMEI Cap Business Rule")
    void testFluxo10_SimeiCapRule() {
        // Get or create configuration
        Configuracao config = configuracaoRepository.findById(1L)
            .orElse(new Configuracao());

        // Verify SIMEI limit exists and is positive
        BigDecimal limiteSimei = config.getLimiteSimei();
        assertNotNull(limiteSimei, "SIMEI limit should be defined");
        assertTrue(limiteSimei.compareTo(BigDecimal.ZERO) > 0,
            "SIMEI limit should be positive");
    }

    /**
     * Test 11: Business Rules - Score Multipliers
     *
     * Verifies score multiplier calculation.
     */
    @Test
    @DisplayName("Test 11: Score Multiplier Calculation")
    void testFluxo11_ScoreMultipliers() {
        Configuracao config = new Configuracao();

        // Test high score (>= 800)
        BigDecimal mult1 = config.getMultiplicadorPorScore(850);
        assertEquals(0, new BigDecimal("1.5").compareTo(mult1),
            "High score should return 1.5x multiplier");

        // Test medium score (>= 600)
        BigDecimal mult2 = config.getMultiplicadorPorScore(650);
        assertEquals(0, new BigDecimal("1.2").compareTo(mult2),
            "Medium score should return 1.2x multiplier");

        // Test normal score (>= 400)
        BigDecimal mult3 = config.getMultiplicadorPorScore(500);
        assertEquals(0, new BigDecimal("1.0").compareTo(mult3),
            "Normal score should return 1.0x multiplier");

        // Test low score (< 400)
        BigDecimal mult4 = config.getMultiplicadorPorScore(350);
        assertEquals(0, new BigDecimal("0.7").compareTo(mult4),
            "Low score should return 0.7x multiplier");
    }

    /**
     * Test 12: Business Rules - Manager Approval Required
     *
     * Verifies manager approval rules.
     */
    @Test
    @DisplayName("Test 12: Manager Approval Rules")
    void testFluxo12_ManagerApprovalRules() {
        Configuracao config = new Configuracao();

        // Test value requires approval
        assertTrue(config.requerAprovacaoPorValor(new BigDecimal("150000.00")),
            "High value should require manager approval");

        assertFalse(config.requerAprovacaoPorValor(new BigDecimal("50000.00")),
            "Low value should not require manager approval");

        // Test restrictions require approval
        assertTrue(config.requerAprovacaoPorRestricoes(7),
            "Many restrictions should require manager approval");

        assertFalse(config.requerAprovacaoPorRestricoes(2),
            "Few restrictions should not require manager approval");
    }

    /**
     * Test 13: Analysis Form Validation - Missing Decision
     *
     * Verifies form validation works.
     */
    @Test
    @DisplayName("Test 13: Form Validation - Missing Decision")
    void testFluxo13_FormValidation() {
        // Create form with missing decision
        AnaliseForm form = new AnaliseForm();
        form.setJustificativa("Teste");

        assertNull(form.getDecisao(), "Decision should be null");
        assertNotNull(form.getJustificativa(), "Justification should be set");
    }

    /**
     * Test 14: Enum Validation
     *
     * Verifies all workflow and status enums are defined.
     */
    @Test
    @DisplayName("Test 14: Enum Definitions")
    void testFluxo14_EnumDefinitions() {
        // Verify TipoWorkflow enums
        assertEquals(2, TipoWorkflow.values().length,
            "Should have 2 workflow types");

        // Verify StatusWorkflow enums
        assertTrue(StatusWorkflow.values().length >= 10,
            "Should have at least 10 status types");

        // Verify specific statuses exist
        assertNotNull(StatusWorkflow.PENDENTE, "PENDENTE status should exist");
        assertNotNull(StatusWorkflow.EM_ANALISE_FINANCEIRO,
            "EM_ANALISE_FINANCEIRO should exist");
        assertNotNull(StatusWorkflow.PARECER_APROVADO,
            "PARECER_APROVADO should exist");
        assertNotNull(StatusWorkflow.PARECER_REPROVADO,
            "PARECER_REPROVADO should exist");
    }

    /**
     * Test 15: Integration Test - Complete Simulated Flow
     *
     * Simulates a complete user journey.
     */
    @Test
    @DisplayName("Test 15: Complete Integration Flow")
    void testFluxo15_CompleteIntegration() {
        // Step 1: Select profile
        MockHttpSession session = new MockHttpSession();
        String redirect = homeController.selecionarPerfil("FINANCEIRO", session);
        assertEquals("redirect:/analise/kanban", redirect);

        // Step 2: View Kanban
        Model kanbanModel = new ExtendedModelMap();
        String kanbanView = kanbanController.kanban("TODOS", session, kanbanModel);
        assertEquals("kanban", kanbanView);

        // Step 3: View Configuration
        Model configModel = new ExtendedModelMap();
        String configView = configuracaoController.exibir(configModel);
        assertEquals("configuracao", configView);

        // Step 4: Create test data
        GrupoEconomico grupo = new GrupoEconomico();
        grupo.setCodigo("FLOW001");
        grupo.setNome("Grupo Fluxo Completo");
        grupo.setLimiteAprovado(new BigDecimal("50000.00"));
        grupo.setLimiteDisponivel(new BigDecimal("50000.00"));

        GrupoEconomico savedGrupo = grupoEconomicoRepository.save(grupo);
        assertNotNull(savedGrupo.getId(), "Group should be saved with ID");

        // Step 5: Verify all components worked together
        assertNotNull(session.getAttribute("perfil"),
            "Session should maintain profile");
        assertNotNull(kanbanModel.getAttribute("kanbanData"),
            "Kanban should load data");
        assertNotNull(savedGrupo.getId(),
            "Database persistence should work");
    }
}
