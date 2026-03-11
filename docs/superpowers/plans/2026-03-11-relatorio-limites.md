# Relatório de Limites Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `/relatorio/limites` — a 3-level drill-down table (Group → CNPJ → Order) showing pending/analysed orders by deposit, total, and approved limit with edit + version history.

**Architecture:** New `HistoricoLimite` entity tracks limit changes per `GrupoEconomico`; `LimiteService` handles atomic limit updates; `RelatorioController` builds `RelatorioLimitesDTO` from existing repositories and renders a Bootstrap `collapse` table; no new JS dependencies.

**Tech Stack:** Spring Boot 4.0, Java 25, Jakarta Persistence, Spring Data JPA, Thymeleaf, HTMX 1.9.10, Bootstrap 5.3.2, JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-03-11-relatorio-limites-design.md`

---

## Chunk 1: Data Model — HistoricoLimite entity, repository, and GrupoEconomico update

### Task 1: Create `HistoricoLimite` entity

**Files:**
- Create: `src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/HistoricoLimite.java`
- Modify: `src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/GrupoEconomico.java`
- Create: `src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/persistence/HistoricoLimiteRepository.java`

- [ ] **Step 1: Write the failing entity test**

Add to existing `src/test/java/AnaliseCredito/Analise_de_Credito/domain/model/EntityTest.java`:

```java
@Test
void historicoLimite_deveSetarDataRegistroAutomaticamente() {
    GrupoEconomico grupo = new GrupoEconomico();
    grupo.setCodigo("123");
    grupo.setNome("Grupo Teste");

    HistoricoLimite h = new HistoricoLimite();
    h.setGrupoEconomico(grupo);
    h.setValor(new BigDecimal("50000.00"));
    h.setResponsavel("FINANCEIRO");
    h.prePersist();

    assertNotNull(h.getDataRegistro());
    assertEquals(new BigDecimal("50000.00"), h.getValor());
    assertEquals("FINANCEIRO", h.getResponsavel());
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -pl . -Dtest="EntityTest#historicoLimite_deveSetarDataRegistroAutomaticamente" -q 2>&1 | tail -20
```

Expected: FAIL — `HistoricoLimite` does not exist yet.

- [ ] **Step 3: Create `HistoricoLimite` entity**

Create `src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/HistoricoLimite.java`:

```java
package AnaliseCredito.Analise_de_Credito.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Histórico de limites aprovados por GrupoEconomico.
 * Cada registro representa uma versão do limite com data e responsável.
 * O limite atual do grupo é sempre igual ao registro mais recente.
 */
@Entity
@Table(name = "historico_limite")
@Data
@NoArgsConstructor
public class HistoricoLimite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_economico_id", nullable = false)
    private GrupoEconomico grupoEconomico;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataRegistro;

    @NotNull
    @Column(nullable = false, length = 100)
    private String responsavel;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 4: Verify no code uses GrupoEconomico all-args constructor**

```bash
grep -r "new GrupoEconomico(" src/ --include="*.java"
```

Expected output: only `new GrupoEconomico()` (no-args). If any call site uses positional args, it will break after adding the new field — fix those call sites first before proceeding.

- [ ] **Step 4b: Add `historicosLimite` to `GrupoEconomico`**

In `src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/GrupoEconomico.java`, add after the `dadosBI` field (around line 73):

```java
/**
 * Histórico de limites aprovados para este grupo.
 * Ordenado do mais recente para o mais antigo.
 * NOTA: O campo limiteAprovado é sempre igual ao valor do registro mais recente.
 * Grupos sem histórico têm limiteAprovado = ZERO (válido — limite ainda não definido).
 */
@OneToMany(mappedBy = "grupoEconomico", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@OrderBy("dataRegistro DESC")
private List<HistoricoLimite> historicosLimite = new ArrayList<>();
```

Also add the import: `import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;`

- [ ] **Step 5: Create `HistoricoLimiteRepository`**

Create `src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/persistence/HistoricoLimiteRepository.java`:

```java
package AnaliseCredito.Analise_de_Credito.infrastructure.persistence;

import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório para HistoricoLimite.
 * Sempre retorna registros em ordem decrescente por data (mais recente primeiro).
 */
@Repository
public interface HistoricoLimiteRepository extends JpaRepository<HistoricoLimite, Long> {

    /**
     * Retorna histórico de limites de um grupo, do mais recente ao mais antigo.
     */
    List<HistoricoLimite> findByGrupoEconomicoIdOrderByDataRegistroDesc(Long grupoId);
}
```

- [ ] **Step 6: Add `findByFiltros` query to `GrupoEconomicoRepository`**

In `src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/persistence/GrupoEconomicoRepository.java`, add after the existing `findByCodigo` method:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Busca grupos com filtro opcional de UF (via clientes) e busca por nome/código.
 * UF: grupo aparece se pelo menos um cliente está na UF informada.
 * Busca: case-insensitive por nome ou exato por código.
 * Passe null para desativar qualquer filtro.
 */
@Query("""
    SELECT DISTINCT g FROM GrupoEconomico g
    JOIN g.clientes c
    WHERE (:uf IS NULL OR c.estado = :uf)
      AND (:busca IS NULL
           OR LOWER(g.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
           OR g.codigo LIKE CONCAT('%', :busca, '%'))
    ORDER BY g.nome
    """)
List<GrupoEconomico> findByFiltros(@Param("uf") String uf, @Param("busca") String busca);

// NOTE: Uses JOIN (not LEFT JOIN) — groups with no clients do not appear.
// This is intentional: a group with no clients has no pedidos to display.
```

- [ ] **Step 7: Run entity test to verify it passes**

```bash
./mvnw test -pl . -Dtest="EntityTest#historicoLimite_deveSetarDataRegistroAutomaticamente" -q 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 8: Run full test suite to confirm no regressions**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: All existing tests pass. Note: Spring Boot will auto-create the `historico_limite` table via `create-drop` in H2.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/HistoricoLimite.java \
        src/main/java/AnaliseCredito/Analise_de_Credito/domain/model/GrupoEconomico.java \
        src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/persistence/HistoricoLimiteRepository.java \
        src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/persistence/GrupoEconomicoRepository.java \
        src/test/java/AnaliseCredito/Analise_de_Credito/domain/model/EntityTest.java
git commit -m "feat: add HistoricoLimite entity and repository with GrupoEconomico association"
```

---

## Chunk 2: LimiteService — atomic limit update with history

### Task 2: Create `LimiteService`

**Files:**
- Create: `src/main/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteService.java`
- Create: `src/test/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteServiceTest.java`:

```java
package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.HistoricoLimiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimiteServiceTest {

    @Mock
    private GrupoEconomicoRepository grupoRepository;

    @Mock
    private HistoricoLimiteRepository historicoRepository;

    @InjectMocks
    private LimiteService limiteService;

    private GrupoEconomico grupo;

    @BeforeEach
    void setUp() {
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(BigDecimal.ZERO);
    }

    @Test
    void atualizarLimite_devePersistirHistoricoEAtualizarGrupo() {
        when(grupoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        limiteService.atualizarLimite(1L, new BigDecimal("75000.00"), "FINANCEIRO");

        // Verifica que HistoricoLimite foi salvo
        ArgumentCaptor<HistoricoLimite> captor = ArgumentCaptor.forClass(HistoricoLimite.class);
        verify(historicoRepository).save(captor.capture());
        HistoricoLimite salvo = captor.getValue();
        assertEquals(new BigDecimal("75000.00"), salvo.getValor());
        assertEquals("FINANCEIRO", salvo.getResponsavel());
        assertEquals(grupo, salvo.getGrupoEconomico());

        // Verifica que o grupo foi atualizado
        assertEquals(new BigDecimal("75000.00"), grupo.getLimiteAprovado());
        verify(grupoRepository).save(grupo);
    }

    @Test
    void atualizarLimite_deveLancarExcecaoParaGrupoInexistente() {
        when(grupoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> limiteService.atualizarLimite(99L, new BigDecimal("1000.00"), "FINANCEIRO"));

        verifyNoInteractions(historicoRepository);
    }

    @Test
    void atualizarLimite_devePermitirZerarLimite() {
        grupo.setLimiteAprovado(new BigDecimal("50000.00"));
        when(grupoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        limiteService.atualizarLimite(1L, BigDecimal.ZERO, "FINANCEIRO");

        assertEquals(BigDecimal.ZERO, grupo.getLimiteAprovado());
        verify(historicoRepository).save(any(HistoricoLimite.class));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw test -pl . -Dtest="LimiteServiceTest" -q 2>&1 | tail -10
```

Expected: FAIL — `LimiteService` does not exist yet.

- [ ] **Step 3: Create `LimiteService`**

Create `src/main/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteService.java`:

```java
package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.HistoricoLimiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Serviço responsável por atualizar o limite aprovado de um GrupoEconomico.
 *
 * Operação atômica: insere HistoricoLimite + atualiza GrupoEconomico.limiteAprovado
 * na mesma transação.
 */
@Service
public class LimiteService {

    @Autowired
    private GrupoEconomicoRepository grupoRepository;

    @Autowired
    private HistoricoLimiteRepository historicoRepository;

    /**
     * Atualiza o limite aprovado de um grupo, gravando o histórico.
     *
     * @param grupoId     ID do GrupoEconomico
     * @param valor       Novo valor do limite
     * @param responsavel Nome do analista que registrou a alteração
     * @throws EntityNotFoundException se o grupo não for encontrado
     */
    @Transactional
    public void atualizarLimite(Long grupoId, BigDecimal valor, String responsavel) {
        GrupoEconomico grupo = grupoRepository.findById(grupoId)
            .orElseThrow(() -> new EntityNotFoundException(
                "GrupoEconomico não encontrado: id=" + grupoId));

        HistoricoLimite historico = new HistoricoLimite();
        historico.setGrupoEconomico(grupo);
        historico.setValor(valor);
        historico.setResponsavel(responsavel);
        historicoRepository.save(historico);

        grupo.setLimiteAprovado(valor);
        grupoRepository.save(grupo);
    }
}
```

- [ ] **Step 4: Run `LimiteServiceTest` to verify all 3 tests pass**

```bash
./mvnw test -pl . -Dtest="LimiteServiceTest" -q 2>&1 | tail -10
```

Expected: 3 tests PASS.

- [ ] **Step 5: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteService.java \
        src/test/java/AnaliseCredito/Analise_de_Credito/application/service/LimiteServiceTest.java
git commit -m "feat: add LimiteService for atomic limit update with history"
```

---

## Chunk 3: DTO and Controller

### Task 3: Create `RelatorioLimitesDTO` and `RelatorioController`

**Files:**
- Create: `src/main/java/AnaliseCredito/Analise_de_Credito/presentation/dto/RelatorioLimitesDTO.java`
- Create: `src/main/java/AnaliseCredito/Analise_de_Credito/presentation/controller/RelatorioController.java`
- Modify: `src/test/java/AnaliseCredito/Analise_de_Credito/presentation/controller/ControllerTest.java`

#### Business logic to implement in controller

**Deposito normalization** (helper method):
```java
private String normalizarDeposito(String deposito) {
    if (deposito == null) return null;
    try {
        return String.valueOf(Integer.parseInt(deposito.trim()));
    } catch (NumberFormatException e) {
        return deposito.trim();
    }
}
```
Values after normalization: `"1"`, `"5"`, `"10"`. Others → excluded from deposit columns but counted in `totalPedidos`.

**Order classification**:
```java
private boolean isAnalisado(Pedido pedido) {
    return pedido.getAnalise() != null
        && StatusWorkflow.FINALIZADO.equals(pedido.getAnalise().getStatusWorkflow());
}
// Use StatusWorkflow.FINALIZADO directly — do NOT use Analise.isFinalizada()
// which also checks dataFim != null and would misclassify migrated records.
```

**DTO construction** — for each `GrupoEconomico` returned by `findByFiltros`, iterate its clients → iterate each client's pedidos → build `PedidoRow` → accumulate into `ClienteRow` → accumulate into `GrupoRow`. Initialize all `BigDecimal` fields to `BigDecimal.ZERO`.

- [ ] **Step 1: Create `RelatorioLimitesDTO`**

Create `src/main/java/AnaliseCredito/Analise_de_Credito/presentation/dto/RelatorioLimitesDTO.java`:

```java
package AnaliseCredito.Analise_de_Credito.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTOs para a tela de Relatório de Limites.
 * Estrutura hierárquica: GrupoRow → ClienteRow → PedidoRow
 */
public class RelatorioLimitesDTO {

    public static class GrupoRow {
        private Long grupoId;
        private String grupoNome;
        private String grupoCodigo;
        private BigDecimal pendenteDep1 = BigDecimal.ZERO;
        private BigDecimal pendenteDep510 = BigDecimal.ZERO;
        private BigDecimal analisadoDep1 = BigDecimal.ZERO;
        private BigDecimal analisadoDep510 = BigDecimal.ZERO;
        private BigDecimal totalPedidos = BigDecimal.ZERO;
        private BigDecimal limiteAprovado = BigDecimal.ZERO;
        private List<ClienteRow> clientes = new ArrayList<>();

        // Getters and setters
        public Long getGrupoId() { return grupoId; }
        public void setGrupoId(Long grupoId) { this.grupoId = grupoId; }
        public String getGrupoNome() { return grupoNome; }
        public void setGrupoNome(String grupoNome) { this.grupoNome = grupoNome; }
        public String getGrupoCodigo() { return grupoCodigo; }
        public void setGrupoCodigo(String grupoCodigo) { this.grupoCodigo = grupoCodigo; }
        public BigDecimal getPendenteDep1() { return pendenteDep1; }
        public void setPendenteDep1(BigDecimal v) { this.pendenteDep1 = v; }
        public BigDecimal getPendenteDep510() { return pendenteDep510; }
        public void setPendenteDep510(BigDecimal v) { this.pendenteDep510 = v; }
        public BigDecimal getAnalisadoDep1() { return analisadoDep1; }
        public void setAnalisadoDep1(BigDecimal v) { this.analisadoDep1 = v; }
        public BigDecimal getAnalisadoDep510() { return analisadoDep510; }
        public void setAnalisadoDep510(BigDecimal v) { this.analisadoDep510 = v; }
        public BigDecimal getTotalPedidos() { return totalPedidos; }
        public void setTotalPedidos(BigDecimal v) { this.totalPedidos = v; }
        public BigDecimal getLimiteAprovado() { return limiteAprovado; }
        public void setLimiteAprovado(BigDecimal v) { this.limiteAprovado = v; }
        public List<ClienteRow> getClientes() { return clientes; }
        public void setClientes(List<ClienteRow> clientes) { this.clientes = clientes; }
    }

    public static class ClienteRow {
        private Long clienteId;
        private String razaoSocial;
        private String cnpj;
        private BigDecimal pendenteDep1 = BigDecimal.ZERO;
        private BigDecimal pendenteDep510 = BigDecimal.ZERO;
        private BigDecimal analisadoDep1 = BigDecimal.ZERO;
        private BigDecimal analisadoDep510 = BigDecimal.ZERO;
        private BigDecimal totalPedidos = BigDecimal.ZERO;
        private List<PedidoRow> pedidos = new ArrayList<>();

        // Getters and setters
        public Long getClienteId() { return clienteId; }
        public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
        public String getRazaoSocial() { return razaoSocial; }
        public void setRazaoSocial(String v) { this.razaoSocial = v; }
        public String getCnpj() { return cnpj; }
        public void setCnpj(String cnpj) { this.cnpj = cnpj; }
        public BigDecimal getPendenteDep1() { return pendenteDep1; }
        public void setPendenteDep1(BigDecimal v) { this.pendenteDep1 = v; }
        public BigDecimal getPendenteDep510() { return pendenteDep510; }
        public void setPendenteDep510(BigDecimal v) { this.pendenteDep510 = v; }
        public BigDecimal getAnalisadoDep1() { return analisadoDep1; }
        public void setAnalisadoDep1(BigDecimal v) { this.analisadoDep1 = v; }
        public BigDecimal getAnalisadoDep510() { return analisadoDep510; }
        public void setAnalisadoDep510(BigDecimal v) { this.analisadoDep510 = v; }
        public BigDecimal getTotalPedidos() { return totalPedidos; }
        public void setTotalPedidos(BigDecimal v) { this.totalPedidos = v; }
        public List<PedidoRow> getPedidos() { return pedidos; }
        public void setPedidos(List<PedidoRow> pedidos) { this.pedidos = pedidos; }
    }

    public static class PedidoRow {
        private Long pedidoId;
        private String numero;
        private LocalDate data;
        private String condicaoPagamento;
        private String deposito;
        private BigDecimal valor = BigDecimal.ZERO;
        private String status; // "Pendente" ou "Analisado"

        // Getters and setters
        public Long getPedidoId() { return pedidoId; }
        public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public LocalDate getData() { return data; }
        public void setData(LocalDate data) { this.data = data; }
        public String getCondicaoPagamento() { return condicaoPagamento; }
        public void setCondicaoPagamento(String v) { this.condicaoPagamento = v; }
        public String getDeposito() { return deposito; }
        public void setDeposito(String deposito) { this.deposito = deposito; }
        public BigDecimal getValor() { return valor; }
        public void setValor(BigDecimal valor) { this.valor = valor; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Totalizador geral (rodapé da tabela).
     * Calculado pelo controller somando todos os GrupoRow.
     */
    public static class Totais {
        public BigDecimal pendenteDep1 = BigDecimal.ZERO;
        public BigDecimal pendenteDep510 = BigDecimal.ZERO;
        public BigDecimal analisadoDep1 = BigDecimal.ZERO;
        public BigDecimal analisadoDep510 = BigDecimal.ZERO;
        public BigDecimal totalPedidos = BigDecimal.ZERO;
        public BigDecimal limiteAprovado = BigDecimal.ZERO;
    }
}
```

- [ ] **Step 2: Write the failing controller tests**

`ControllerTest` currently has no MockMvc setup. Replace the entire class with:

```java
package AnaliseCredito.Analise_de_Credito.presentation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests using MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Spring context loads with all controllers
    }

    @Test
    void relatorio_deveRetornarPaginaComGrupos() throws Exception {
        mockMvc.perform(get("/relatorio/limites")
                .sessionAttr("perfil", "FINANCEIRO"))
            .andExpect(status().isOk())
            .andExpect(view().name("relatorio-limites"))
            .andExpect(model().attributeExists("grupos"))
            .andExpect(model().attributeExists("perfil"));
    }

    @Test
    void relatorio_postLimite_deveRedirecionarAposAtualizar() throws Exception {
        mockMvc.perform(post("/relatorio/limites/1/limite")
                .param("valor", "50000.00")
                .param("responsavel", "FINANCEIRO")
                .sessionAttr("perfil", "FINANCEIRO"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/relatorio/limites"));
    }

    @Test
    void relatorio_historico_deveRetornarFragment() throws Exception {
        mockMvc.perform(get("/relatorio/limites/1/historico")
                .sessionAttr("perfil", "FINANCEIRO"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/historico-limite-modal :: corpo"));
    }
}
```

> **Note:** `postLimite` will fail with a `EntityNotFoundException` for `id=1` until data is seeded. That is expected in the test environment — the test only validates the HTTP contract (redirect). If this causes issues, mock `LimiteService` or use a `@MockBean`.

- [ ] **Step 3: Run controller tests to verify they fail**

```bash
./mvnw test -pl . -Dtest="ControllerTest#relatorio_deveRetornarPaginaComGrupos,ControllerTest#relatorio_postLimite_deveRedirecionarAposAtualizar,ControllerTest#relatorio_historico_deveRetornarFragment" -q 2>&1 | tail -15
```

Expected: FAIL — `RelatorioController` does not exist yet.

- [ ] **Step 4: Create `RelatorioController`**

Create `src/main/java/AnaliseCredito/Analise_de_Credito/presentation/controller/RelatorioController.java`:

```java
package AnaliseCredito.Analise_de_Credito.presentation.controller;

import AnaliseCredito.Analise_de_Credito.application.service.LimiteService;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.Cliente;
import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import AnaliseCredito.Analise_de_Credito.domain.model.Pedido;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.HistoricoLimiteRepository;
import AnaliseCredito.Analise_de_Credito.presentation.dto.RelatorioLimitesDTO;
import AnaliseCredito.Analise_de_Credito.presentation.dto.RelatorioLimitesDTO.ClienteRow;
import AnaliseCredito.Analise_de_Credito.presentation.dto.RelatorioLimitesDTO.GrupoRow;
import AnaliseCredito.Analise_de_Credito.presentation.dto.RelatorioLimitesDTO.PedidoRow;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * RelatorioController — Tela de Relatório de Limites.
 *
 * Exibe tabela hierárquica (Grupo → CNPJ → Pedido) com somas de pedidos
 * por depósito (1 e 5+10), total e limite aprovado. Permite editar limite.
 */
@Controller
@RequestMapping("/relatorio")
public class RelatorioController {

    @Autowired
    private GrupoEconomicoRepository grupoRepository;

    @Autowired
    private HistoricoLimiteRepository historicoRepository;

    @Autowired
    private LimiteService limiteService;

    // ────────────────────────────────────────────────────────────────────────
    // GET /relatorio/limites — renderiza a tela principal
    // ────────────────────────────────────────────────────────────────────────

    @GetMapping("/limites")
    @Transactional(readOnly = true)
    public String listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String uf,
            HttpSession session,
            Model model) {

        String perfil = (String) session.getAttribute("perfil");
        List<GrupoEconomico> grupos = grupoRepository.findByFiltros(
            emptyToNull(uf), emptyToNull(busca));

        List<GrupoRow> grupoRows = new ArrayList<>();
        for (GrupoEconomico grupo : grupos) {
            grupoRows.add(montarGrupoRow(grupo));
        }

        // Compute footer totals
        RelatorioLimitesDTO.Totais totais = new RelatorioLimitesDTO.Totais();
        for (GrupoRow gr : grupoRows) {
            totais.pendenteDep1   = totais.pendenteDep1.add(gr.getPendenteDep1());
            totais.pendenteDep510 = totais.pendenteDep510.add(gr.getPendenteDep510());
            totais.analisadoDep1  = totais.analisadoDep1.add(gr.getAnalisadoDep1());
            totais.analisadoDep510= totais.analisadoDep510.add(gr.getAnalisadoDep510());
            totais.totalPedidos   = totais.totalPedidos.add(gr.getTotalPedidos());
            totais.limiteAprovado = totais.limiteAprovado.add(gr.getLimiteAprovado());
        }

        model.addAttribute("grupos", grupoRows);
        model.addAttribute("totais", totais);
        model.addAttribute("perfil", perfil);
        model.addAttribute("busca", busca);
        model.addAttribute("uf", uf);
        return "relatorio-limites";
    }

    // ────────────────────────────────────────────────────────────────────────
    // POST /relatorio/limites/{grupoId}/limite — salva novo limite
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping("/limites/{grupoId}/limite")
    public String atualizarLimite(
            @PathVariable Long grupoId,
            @RequestParam BigDecimal valor,
            @RequestParam String responsavel) {

        limiteService.atualizarLimite(grupoId, valor, responsavel);
        return "redirect:/relatorio/limites";
    }

    // ────────────────────────────────────────────────────────────────────────
    // GET /relatorio/limites/{grupoId}/historico — fragment HTMX
    // ────────────────────────────────────────────────────────────────────────

    @GetMapping("/limites/{grupoId}/historico")
    @Transactional(readOnly = true)
    public String historico(
            @PathVariable Long grupoId,
            Model model) {

        List<HistoricoLimite> historico =
            historicoRepository.findByGrupoEconomicoIdOrderByDataRegistroDesc(grupoId);
        model.addAttribute("historico", historico);
        return "fragments/historico-limite-modal :: corpo";
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ────────────────────────────────────────────────────────────────────────

    private GrupoRow montarGrupoRow(GrupoEconomico grupo) {
        GrupoRow grupoRow = new GrupoRow();
        grupoRow.setGrupoId(grupo.getId());
        grupoRow.setGrupoNome(grupo.getNome());
        grupoRow.setGrupoCodigo(grupo.getCodigo());
        grupoRow.setLimiteAprovado(grupo.getLimiteAprovado());

        for (Cliente cliente : grupo.getClientes()) {
            ClienteRow clienteRow = montarClienteRow(cliente);
            grupoRow.getClientes().add(clienteRow);

            // Acumula no grupo
            grupoRow.setPendenteDep1(grupoRow.getPendenteDep1().add(clienteRow.getPendenteDep1()));
            grupoRow.setPendenteDep510(grupoRow.getPendenteDep510().add(clienteRow.getPendenteDep510()));
            grupoRow.setAnalisadoDep1(grupoRow.getAnalisadoDep1().add(clienteRow.getAnalisadoDep1()));
            grupoRow.setAnalisadoDep510(grupoRow.getAnalisadoDep510().add(clienteRow.getAnalisadoDep510()));
            grupoRow.setTotalPedidos(grupoRow.getTotalPedidos().add(clienteRow.getTotalPedidos()));
        }

        return grupoRow;
    }

    private ClienteRow montarClienteRow(Cliente cliente) {
        ClienteRow clienteRow = new ClienteRow();
        clienteRow.setClienteId(cliente.getId());
        clienteRow.setRazaoSocial(cliente.getRazaoSocial());
        clienteRow.setCnpj(cliente.getCnpj());

        for (Pedido pedido : cliente.getPedidos()) {
            PedidoRow pedidoRow = montarPedidoRow(pedido);
            clienteRow.getPedidos().add(pedidoRow);

            BigDecimal valor = pedido.getValor() != null ? pedido.getValor() : BigDecimal.ZERO;
            String dep = normalizarDeposito(pedido.getDeposito());
            boolean analisado = isAnalisado(pedido);

            // totalPedidos inclui todos (mesmo deposito desconhecido)
            clienteRow.setTotalPedidos(clienteRow.getTotalPedidos().add(valor));

            if ("1".equals(dep)) {
                if (analisado) clienteRow.setAnalisadoDep1(clienteRow.getAnalisadoDep1().add(valor));
                else           clienteRow.setPendenteDep1(clienteRow.getPendenteDep1().add(valor));
            } else if ("5".equals(dep) || "10".equals(dep)) {
                if (analisado) clienteRow.setAnalisadoDep510(clienteRow.getAnalisadoDep510().add(valor));
                else           clienteRow.setPendenteDep510(clienteRow.getPendenteDep510().add(valor));
            }
            // deposito null ou desconhecido: só soma em totalPedidos (acima)
        }

        return clienteRow;
    }

    private PedidoRow montarPedidoRow(Pedido pedido) {
        PedidoRow row = new PedidoRow();
        row.setPedidoId(pedido.getId());
        row.setNumero(pedido.getNumero());
        row.setData(pedido.getData());
        row.setCondicaoPagamento(pedido.getCondicaoPagamento());
        row.setDeposito(pedido.getDeposito());
        row.setValor(pedido.getValor() != null ? pedido.getValor() : BigDecimal.ZERO);
        row.setStatus(isAnalisado(pedido) ? "Analisado" : "Pendente");
        return row;
    }

    /**
     * Normaliza o campo deposito para comparação: "05" → "5", "010" → "10".
     * Retorna null se valor for nulo.
     */
    private String normalizarDeposito(String deposito) {
        if (deposito == null) return null;
        try {
            return String.valueOf(Integer.parseInt(deposito.trim()));
        } catch (NumberFormatException e) {
            return deposito.trim();
        }
    }

    /**
     * Pedido é "Analisado" se tem Analise com status FINALIZADO.
     * Usa StatusWorkflow.FINALIZADO diretamente — não usa Analise.isFinalizada()
     * para evitar divergência com análises que não têm dataFim setado.
     */
    private boolean isAnalisado(Pedido pedido) {
        return pedido.getAnalise() != null
            && StatusWorkflow.FINALIZADO.equals(pedido.getAnalise().getStatusWorkflow());
    }

    /**
     * Converte string vazia em null para queries com parâmetros opcionais.
     */
    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
```

- [ ] **Step 5: Run controller tests**

```bash
./mvnw test -pl . -Dtest="ControllerTest#relatorio_deveRetornarPaginaComGrupos,ControllerTest#relatorio_postLimite_deveRedirecionarAposAtualizar,ControllerTest#relatorio_historico_deveRetornarFragment" -q 2>&1 | tail -15
```

Expected: Tests pass (may fail with template-not-found — that is OK; proceed to Task 4 which creates the templates).

- [ ] **Step 6: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: All pre-existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/AnaliseCredito/Analise_de_Credito/presentation/dto/RelatorioLimitesDTO.java \
        src/main/java/AnaliseCredito/Analise_de_Credito/presentation/controller/RelatorioController.java \
        src/test/java/AnaliseCredito/Analise_de_Credito/presentation/controller/ControllerTest.java
git commit -m "feat: add RelatorioLimitesDTO and RelatorioController with deposit drill-down logic"
```

---

## Chunk 4: Templates — main view, history fragment, and sidebar link

### Task 4: Create Thymeleaf templates

**Files:**
- Create: `src/main/resources/templates/relatorio-limites.html`
- Create: `src/main/resources/templates/fragments/historico-limite-modal.html`
- Modify: `src/main/resources/templates/fragments/layout.html`

- [ ] **Step 1: Add "Relatório" link to sidebar in `layout.html`**

In `src/main/resources/templates/fragments/layout.html`, inside the `<nav class="sidebar-nav">` section, add after the "Importação XLSX" link (around line 77):

```html
<a th:href="@{/relatorio/limites}"
   th:classappend="${paginaAtiva == 'relatorio'} ? ' active' : ''"
   class="sidebar-link">
    <svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
    Relatório de Limites
</a>
```

- [ ] **Step 2: Create the history fragment**

Create `src/main/resources/templates/fragments/historico-limite-modal.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>

<th:block th:fragment="corpo">
    <div th:if="${#lists.isEmpty(historico)}" class="text-muted text-center py-3">
        Nenhum histórico registrado.
    </div>
    <table th:unless="${#lists.isEmpty(historico)}" class="table table-sm table-striped mb-0">
        <thead class="table-light">
            <tr>
                <th>Data</th>
                <th class="text-end">Valor</th>
                <th>Responsável</th>
            </tr>
        </thead>
        <tbody>
            <tr th:each="h : ${historico}">
                <td th:text="${#temporals.format(h.dataRegistro, 'dd/MM/yyyy HH:mm')}">01/01/2026 10:00</td>
                <td class="text-end"
                    th:text="${'R$ ' + #numbers.formatDecimal(h.valor, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                <td th:text="${h.responsavel}">FINANCEIRO</td>
            </tr>
        </tbody>
    </table>
</th:block>

</body>
</html>
```

- [ ] **Step 3: Create the main template `relatorio-limites.html`**

Create `src/main/resources/templates/relatorio-limites.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:hx="http://www.w3.org/1999/xhtml">
<head th:replace="~{fragments/layout :: head('Relatório de Limites')}"></head>
<body class="has-sidebar">

<div th:replace="~{fragments/layout :: sidebar('relatorio', ${perfil})}"></div>

<div class="main-content">
    <div th:replace="~{fragments/layout :: topbar('Relatório de Limites', ${perfil})}"></div>

    <div class="content-area p-4">

        <!-- ── Filtros ── -->
        <form method="GET" action="/relatorio/limites" class="row g-2 mb-4">
            <div class="col-md-5">
                <input type="text" name="busca" class="form-control"
                       placeholder="Buscar por nome ou CNPJ do grupo"
                       th:value="${busca}">
            </div>
            <div class="col-md-3">
                <select name="uf" class="form-select">
                    <option value="">Todos os estados</option>
                    <option th:each="estado : ${ {'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'} }"
                            th:value="${estado}" th:text="${estado}"
                            th:selected="${estado == uf}">SP</option>
                </select>
            </div>
            <div class="col-auto">
                <button type="submit" class="btn btn-primary">Filtrar</button>
            </div>
            <div class="col-auto" th:if="${busca != null or uf != null}">
                <a href="/relatorio/limites" class="btn btn-outline-secondary">Limpar</a>
            </div>
        </form>

        <!-- ── Tabela hierárquica ── -->
        <div class="table-responsive">
        <table class="table table-bordered table-hover align-middle mb-0" id="tabela-limites">
            <thead class="table-dark">
                <tr>
                    <th style="min-width:220px">Grupo / CNPJ / Pedido</th>
                    <th class="text-end">Pend. Dep.1</th>
                    <th class="text-end">Pend. Dep.5+10</th>
                    <th class="text-end">Anal. Dep.1</th>
                    <th class="text-end">Anal. Dep.5+10</th>
                    <th class="text-end">Total Pedidos</th>
                    <th class="text-end">Limite Aprovado</th>
                    <th class="text-center" style="width:60px"></th>
                </tr>
            </thead>
            <!--
                IMPORTANT THYMELEAF PATTERN: Use th:block + multiple <tbody> elements
                to create nested rows. Do NOT use sibling <tr th:each> in a flat <tbody>
                because inner iterations lose access to outer scope variables.

                Structure:
                - <tbody> per group: one <tr> for group header
                - <tbody id="clientes-{id}"> collapse: one <tr> per client
                - <tbody id="pedidos-{clienteId}"> collapse: one <tr> per pedido
            -->
            <th:block th:each="g : ${grupos}">

                <!-- ── Nível 1: Grupo (header row) ── -->
                <tbody>
                <tr class="table-primary fw-semibold"
                    style="cursor:pointer"
                    th:attr="data-bs-toggle='collapse', data-bs-target='#clientes-' + ${g.grupoId}">
                    <td>
                        <span class="me-2 toggle-icon">▶</span>
                        <span th:text="${g.grupoNome}">Grupo XPTO</span>
                        <small class="text-muted ms-2" th:text="${g.grupoCodigo}">(código)</small>
                    </td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(g.pendenteDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(g.pendenteDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(g.analisadoDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(g.analisadoDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end fw-bold" th:text="${'R$ ' + #numbers.formatDecimal(g.totalPedidos, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(g.limiteAprovado, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-center">
                        <button type="button" class="btn btn-sm btn-outline-primary"
                                th:attr="data-grupo-id=${g.grupoId}, data-grupo-nome=${g.grupoNome}, data-limite-atual=${g.limiteAprovado}"
                                data-bs-toggle="modal" data-bs-target="#modal-editar-limite"
                                onclick="abrirModalLimite(this); event.stopPropagation();"
                                title="Editar limite">✏️</button>
                    </td>
                </tr>
                </tbody>

                <!-- ── Nível 2: Clientes (collapse tbody, one row per client) ── -->
                <tbody th:id="'clientes-' + ${g.grupoId}" class="collapse">
                <th:block th:each="c : ${g.clientes}">
                    <tr class="table-secondary">
                        <td style="padding-left: 2.5rem"
                            style="cursor:pointer"
                            th:attr="data-bs-toggle='collapse', data-bs-target='#pedidos-' + ${c.clienteId}">
                            <span class="me-2 toggle-icon">▶</span>
                            <span th:text="${c.razaoSocial}">Razão Social</span>
                            <small class="text-muted ms-2" th:text="${c.cnpj}">(CNPJ)</small>
                        </td>
                        <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(c.pendenteDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(c.pendenteDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(c.analisadoDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(c.analisadoDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td class="text-end fw-bold" th:text="${'R$ ' + #numbers.formatDecimal(c.totalPedidos, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td class="text-end text-muted">—</td>
                        <td></td>
                    </tr>
                </th:block>
                </tbody>

                <!-- ── Nível 3: Pedidos (one collapse tbody per client) ── -->
                <th:block th:each="c : ${g.clientes}">
                <tbody th:id="'pedidos-' + ${c.clienteId}" class="collapse">
                    <tr th:each="p : ${c.pedidos}" class="bg-white">
                        <td style="padding-left: 4.5rem">
                            <span th:text="${p.numero}">PED-001</span>
                            <span class="badge ms-2"
                                  th:classappend="${p.status == 'Analisado'} ? 'bg-success' : 'bg-warning text-dark'"
                                  th:text="${p.status}">Pendente</span>
                        </td>
                        <td class="text-muted small"
                            th:text="${p.data != null ? #temporals.format(p.data, 'dd/MM/yyyy') : '—'}">01/01/2026</td>
                        <td class="text-muted small" th:text="${p.condicaoPagamento}">30/60/90</td>
                        <td class="text-center small" th:text="${p.deposito != null ? 'Dep.' + p.deposito : '—'}">Dep.1</td>
                        <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(p.valor, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                        <td colspan="3"></td>
                    </tr>
                </tbody>
                </th:block>

            </th:block>

            <!-- ── Rodapé totalizador — usa objeto Totais pré-calculado no controller ── -->
            <tfoot class="table-dark fw-bold" th:if="${not #lists.isEmpty(grupos)}">
                <tr>
                    <td>TOTAL</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.pendenteDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.pendenteDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.analisadoDep1, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.analisadoDep510, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.totalPedidos, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td class="text-end" th:text="${'R$ ' + #numbers.formatDecimal(totais.limiteAprovado, 1, 'POINT', 2, 'COMMA')}">R$ 0,00</td>
                    <td></td>
                </tr>
            </tfoot>
        </table>
        </div>

        <!-- ── Mensagem lista vazia ── -->
        <div th:if="${#lists.isEmpty(grupos)}" class="text-center text-muted py-5">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="mb-3 d-block mx-auto opacity-50">
                <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/>
            </svg>
            <p>Nenhum grupo encontrado com os filtros aplicados.</p>
        </div>

    </div><!-- /content-area -->
</div><!-- /main-content -->

<!-- ════════════════════════════════════════════════
     Modal: Editar Limite
     ════════════════════════════════════════════════ -->
<div id="modal-editar-limite" class="modal fade" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Editar Limite Aprovado</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <form id="form-editar-limite" method="POST" action="#">
                <div class="modal-body">
                    <p class="text-muted mb-3" id="modal-grupo-nome">Grupo</p>
                    <div class="mb-3">
                        <label class="form-label fw-semibold">Novo limite (R$)</label>
                        <input type="number" name="valor" class="form-control"
                               step="0.01" min="0" required placeholder="Ex: 50000.00">
                    </div>
                    <div class="mb-3">
                        <label class="form-label">Responsável</label>
                        <input type="text" name="responsavel" class="form-control"
                               th:value="${perfil}" required maxlength="100">
                    </div>
                    <div class="mb-1">
                        <a href="#" id="link-ver-historico" class="text-muted small"
                           hx-target="#modal-historico-body"
                           hx-swap="innerHTML"
                           onclick="document.getElementById('modal-historico').querySelector('[data-bs-toggle]') || new bootstrap.Modal(document.getElementById('modal-historico')).show(); return false;">
                            Ver histórico de limites
                        </a>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>
                    <button type="submit" class="btn btn-primary">Salvar</button>
                </div>
            </form>
        </div>
    </div>
</div>

<!-- ════════════════════════════════════════════════
     Modal: Histórico de Limites (container inline)
     ════════════════════════════════════════════════ -->
<div id="modal-historico" class="modal fade" tabindex="-1">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Histórico de Limites</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div id="modal-historico-body" class="text-muted text-center py-3">
                    Carregando...
                </div>
            </div>
        </div>
    </div>
</div>

<div th:replace="~{fragments/layout :: scripts}"></div>
<script th:src="@{/webjars/htmx.org/1.9.10/dist/htmx.min.js}"></script>

<script>
// Abre modal de edição de limite com dados do grupo clicado
function abrirModalLimite(btn) {
    const grupoId = btn.dataset.grupoId;
    const grupoNome = btn.dataset.grupoNome;
    const limiteAtual = btn.dataset.limiteAtual;

    document.getElementById('modal-grupo-nome').textContent = grupoNome;
    document.getElementById('form-editar-limite').action = '/relatorio/limites/' + grupoId + '/limite';

    const inputValor = document.querySelector('#form-editar-limite input[name="valor"]');
    if (inputValor) inputValor.value = limiteAtual;

    // Configura link de histórico com HTMX
    const linkHistorico = document.getElementById('link-ver-historico');
    linkHistorico.setAttribute('hx-get', '/relatorio/limites/' + grupoId + '/historico');
    htmx.process(linkHistorico);

    linkHistorico.onclick = function(e) {
        e.preventDefault();
        htmx.trigger(linkHistorico, 'click');
        new bootstrap.Modal(document.getElementById('modal-historico')).show();
    };
}

// Ícone ▶/▼ no collapse
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-bs-toggle="collapse"]').forEach(function(el) {
        const target = el.dataset.bsTarget || el.getAttribute('data-bs-target');
        if (!target) return;
        const collapseEl = document.querySelector(target);
        if (!collapseEl) return;
        collapseEl.addEventListener('show.bs.collapse', function() {
            el.querySelector('.toggle-icon') && (el.querySelector('.toggle-icon').textContent = '▼');
        });
        collapseEl.addEventListener('hide.bs.collapse', function() {
            el.querySelector('.toggle-icon') && (el.querySelector('.toggle-icon').textContent = '▶');
        });
    });
});
</script>

</body>
</html>
```

> **Note on tfoot totals:** Thymeleaf SpEL `stream()` + `reduce()` with `T(java.math.BigDecimal)` may not work in all versions. If it fails at runtime, replace the stream expression with a pre-computed `totais` object passed from the controller. Add `RelatorioLimitesDTO.Totais` (with the 5 sum fields) and compute it in `RelatorioController.listar()` before calling `model.addAttribute`.

- [ ] **Step 4: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: All tests pass. Controller tests for the 3 relatorio endpoints should now pass fully (templates exist).

- [ ] **Step 5: Start the application and verify manually**

```bash
./mvnw spring-boot:run &
sleep 10
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/relatorio/limites
```

Expected: `302` (redirect to profile selection, since no session). Then navigate manually via browser, import data, and verify the table renders correctly.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/relatorio-limites.html \
        src/main/resources/templates/fragments/historico-limite-modal.html \
        src/main/resources/templates/fragments/layout.html
git commit -m "feat: add Relatorio de Limites templates with 3-level drill-down and limit edit modal"
```

---

## Post-Implementation Notes

### Session guard

If the app redirects unauthenticated users (no `perfil` in session), verify `HomeController` handles the redirect before `/relatorio/limites` is accessible.
