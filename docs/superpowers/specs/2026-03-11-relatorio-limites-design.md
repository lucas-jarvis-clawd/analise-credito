# Design: Relatório de Limites por Grupo Econômico

**Data:** 2026-03-11
**Status:** Aprovado (v2 — issues de revisão corrigidos)

---

## Contexto

O sistema de análise de crédito possui um Kanban para acompanhamento de análises e um wizard de análise por pedido. Falta uma visão consolidada tabular que permita ao analista ver, em um único lugar, o total de pedidos por grupo econômico discriminados por depósito (1 e 5+10), o total geral, e o limite de crédito aprovado pelo financeiro — com histórico de alterações.

---

## Objetivo

Criar a tela `/relatorio/limites` com uma tabela hierárquica (drill-down em 3 níveis: Grupo → CNPJ → Pedido) que exiba, por grupo econômico:

- Pedidos pendentes de análise por depósito
- Pedidos já analisados por depósito
- Total geral de pedidos
- Limite aprovado (editável com histórico)

---

## Modelo de Dados

### Nova entidade: `HistoricoLimite`

```java
@Entity
@Table(name = "historico_limite")
public class HistoricoLimite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
    }
}
```

### Novo repositório: `HistoricoLimiteRepository`

```java
public interface HistoricoLimiteRepository extends JpaRepository<HistoricoLimite, Long> {
    List<HistoricoLimite> findByGrupoEconomicoIdOrderByDataRegistroDesc(Long grupoId);
}
```

A ordenação `OrderByDataRegistroDesc` é obrigatória: o primeiro item da lista é sempre o mais recente (limite atual). O modal de histórico exibe na mesma ordem (mais recente primeiro).

### Alteração em `GrupoEconomico`

Adiciona relacionamento:

```java
@OneToMany(mappedBy = "grupoEconomico", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@OrderBy("dataRegistro DESC")
private List<HistoricoLimite> historicosLimite = new ArrayList<>();
```

> **Nota Lombok:** `GrupoEconomico` usa `@Data` + `@AllArgsConstructor`. O novo campo será incluído no construtor gerado pelo Lombok. Porém, todo o código existente que cria `GrupoEconomico` usa o construtor sem argumentos (`new GrupoEconomico()` + setters), então não há call sites quebrados. Confirmar antes de gerar código que nenhum teste usa o all-args constructor.

O campo `limiteAprovado` (BigDecimal, não-nulo, default `ZERO`) permanece como fonte de verdade atual. Grupos sem nenhum `HistoricoLimite` têm `limiteAprovado = ZERO` — estado válido, representando grupos que ainda não tiveram limite definido. O template e o modal de histórico tratam lista vazia exibindo mensagem "Nenhum histórico registrado."

Ao gravar um novo limite: (1) insere `HistoricoLimite`, (2) atualiza `GrupoEconomico.limiteAprovado` — na mesma transação, dentro de `LimiteService`.

---

## Novo Service: `LimiteService`

Para seguir o padrão arquitetural do projeto (lógica de negócio em `application/service/`, não no controller), criar:

```
application/service/LimiteService.java
```

Responsabilidade: método `@Transactional atualizarLimite(Long grupoId, BigDecimal valor, String responsavel)` que:
1. Carrega `GrupoEconomico` pelo ID (lança `EntityNotFoundException` se não encontrado)
2. Cria e persiste `HistoricoLimite`
3. Atualiza `grupoEconomico.setLimiteAprovado(valor)`
4. Salva `GrupoEconomico`

---

## Regras de Negócio

### Campo `deposito` em `Pedido`

- Valores válidos após importação: `"1"`, `"5"`, `"10"` (strings sem zero-padding)
- Zero-padding (`"05"`, `"010"`) deve ser tratado como equivalente: normalizar com `String.stripLeading('0')` ou comparação após `Integer.parseInt` ao montar os `PedidoRow`
- Agrupamento: **Dep. 1** = `"1"` | **Dep. 5+10** = `"5"` ou `"10"` (após normalização)
- **Regra para valores nulos ou desconhecidos:** pedidos com `deposito` nulo ou fora de `{"1", "5", "10"}` (após normalização) são **excluídos das colunas de depósito específico** mas **incluídos em `totalPedidos`**. O status de cada coluna deve somar `BigDecimal.ZERO` se não houver pedidos válidos no depósito.

### Classificação de pedidos

- **Pendente de análise:** pedido sem `Analise`, ou com `Analise` cujo `statusWorkflow != StatusWorkflow.FINALIZADO`
- **Analisado:** pedido com `Analise` onde `StatusWorkflow.FINALIZADO.equals(analise.getStatusWorkflow())`

> **Nota:** usar `StatusWorkflow.FINALIZADO.equals(analise.getStatusWorkflow())` diretamente, não o método `isFinalizada()` de `Analise.java`, pois este também exige `dataFim != null` e poderia divergir de análises migradas.

### Colunas de valor por grupo/CNPJ

| Coluna | Descrição |
|---|---|
| Pendente Dep.1 | Soma de `Pedido.valor` dos pedidos pendentes com `deposito = "1"` |
| Pendente Dep.5+10 | Soma de `Pedido.valor` dos pedidos pendentes com `deposito` in `{"5", "10"}` |
| Analisado Dep.1 | Soma de `Pedido.valor` dos pedidos analisados com `deposito = "1"` |
| Analisado Dep.5+10 | Soma de `Pedido.valor` dos pedidos analisados com `deposito` in `{"5", "10"}` |
| Total Pedidos | Soma de **todos** os pedidos do grupo (incluindo os com deposito nulo/desconhecido) |
| Limite Aprovado | `GrupoEconomico.limiteAprovado` (nível 1 apenas; nível 2 exibe "—") |

### Coluna "Limite Aprovado" no nível CNPJ

O limite pertence ao grupo, não ao cliente. Na linha de nível 2 (CNPJ), a coluna "Limite Aprovado" exibe "—" (em-dash). Não repetir o valor do grupo em cada linha de cliente para evitar confusão.

### Filtro por UF

`GrupoEconomico` não possui campo `estado`. O filtro por UF é aplicado via join com `Cliente.estado`: **um grupo aparece se pelo menos um de seus clientes pertence à UF selecionada**.

A query deve viver em `GrupoEconomicoRepository` como `@Query` JPQL:

```java
@Query("""
    SELECT DISTINCT g FROM GrupoEconomico g
    JOIN g.clientes c
    WHERE (:uf IS NULL OR c.estado = :uf)
      AND (:busca IS NULL OR LOWER(g.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
           OR g.codigo LIKE CONCAT('%', :busca, '%'))
    ORDER BY g.nome
    """)
List<GrupoEconomico> findByFiltros(
    @Param("uf") String uf,
    @Param("busca") String busca
);
```

`RelatorioController` chama esse método passando os params como `null` quando não informados.

### Atualização de limite

- Disponível no relatório (modal ✏️) e, futuramente, no wizard de análise
- Ao salvar: chama `LimiteService.atualizarLimite(grupoId, valor, responsavel)`
- O responsável é pré-preenchido com `session.getAttribute("perfil")` (chave usada em todo o projeto, ver `KanbanController`)

---

## Tela: `/relatorio/limites`

### Barra de filtros

- Busca por nome ou CNPJ do grupo (texto livre, filtro server-side, `WHERE g.nome LIKE %busca% OR g.codigo LIKE %busca%`)
- Filtro por UF (select; aplica join via Cliente conforme regra acima)
- Botão "Filtrar" → `GET /relatorio/limites?busca=...&uf=...`

### Tabela hierárquica

#### Nível 1 — Grupo Econômico

Linha com fundo (`table-primary`). Expansível via `data-bs-toggle="collapse"` do Bootstrap, com ícone ▶/▼.

Colunas:
```
[▶] Nome do Grupo | Pend. Dep.1 | Pend. Dep.5+10 | Anal. Dep.1 | Anal. Dep.5+10 | Total Pedidos | Limite Aprovado | [✏️ Editar]
```

#### Nível 2 — CNPJ/Cliente

Linha indentada (`table-secondary`). Expansível da mesma forma. Exibe razão social + CNPJ formatado.

Colunas (mesmas do nível 1, sem a coluna Limite Aprovado que exibe "—"):
```
[▶] Razão Social (CNPJ) | Pend. Dep.1 | Pend. Dep.5+10 | Anal. Dep.1 | Anal. Dep.5+10 | Total Pedidos | — | (sem botão editar)
```

#### Nível 3 — Pedido

Linha mais indentada (fundo branco). Sem botão de expansão.

Colunas:
```
Número | Data | Condição Pagamento | Depósito | Valor | Status
```

#### Rodapé totalizador

Linha `<tfoot>` bold com soma de todas as colunas de valor (Pend. Dep.1, Pend. Dep.5+10, Anal. Dep.1, Anal. Dep.5+10, Total Pedidos) somando todos os grupos visíveis. Coluna "Limite Aprovado" no rodapé soma todos os `limiteAprovado` dos grupos.

### Modal: Editar Limite

Disparado pelo botão ✏️ na linha do grupo. Bootstrap modal com formulário HTML padrão (não HTMX) para garantir comportamento previsível:

```html
<form method="POST" action="/relatorio/limites/{grupoId}/limite">
  <input type="hidden" name="grupoId" value="...">
  <input type="number" name="valor" required step="0.01">
  <input type="text" name="responsavel" value="${perfil}">
  <button type="submit">Salvar</button>
</form>
```

O `POST` faz redirect para `GET /relatorio/limites` (full page reload), fechando o modal naturalmente.

Link "Ver histórico de limites" abre o modal de histórico via `hx-get="/relatorio/limites/{grupoId}/historico" hx-target="#modal-historico-body"`.

### Modal: Histórico de Limites

Container modal separado definido **inline em `relatorio-limites.html`** (não no fragment):
```html
<div id="modal-historico" class="modal fade" ...>
  <div id="modal-historico-body"><!-- preenchido via HTMX --></div>
</div>
```

O arquivo `templates/fragments/historico-limite-modal.html` contém apenas o conteúdo do body:
```html
<th:block th:fragment="corpo">
  <table>...</table>
</th:block>
```

O HTMX carrega: `hx-get="/relatorio/limites/{grupoId}/historico" hx-target="#modal-historico-body"`.
O controller retorna: `"fragments/historico-limite-modal :: corpo"`.
Exibe tabela com colunas: Data, Valor, Responsável (ordenada por data DESC — mais recente primeiro). Se lista vazia, exibe "Nenhum histórico registrado."

---

## Controller: `RelatorioController`

```
GET  /relatorio/limites
     Params: busca (String, opcional), uf (String, opcional)
     Session: "perfil" (String)
     → carrega grupos via query com filtros opcionais
     → monta List<GrupoRow> via LimiteService ou montagem inline
     → renderiza relatorio-limites.html

POST /relatorio/limites/{grupoId}/limite
     Params de form: valor (BigDecimal), responsavel (String)
     → chama LimiteService.atualizarLimite(grupoId, valor, responsavel)
     → redirect: "redirect:/relatorio/limites"

GET  /relatorio/limites/{grupoId}/historico
     → busca HistoricoLimiteRepository.findByGrupoEconomicoIdOrderByDataRegistroDesc(grupoId)
     → retorna fragment "fragments/historico-limite-modal :: corpo"
```

---

## DTOs

### `RelatorioLimitesDTO`

```java
class GrupoRow {
    Long grupoId;
    String grupoNome;
    String grupoCodigo;
    BigDecimal pendenteDep1;      // default ZERO
    BigDecimal pendenteDep510;    // default ZERO
    BigDecimal analisadoDep1;     // default ZERO
    BigDecimal analisadoDep510;   // default ZERO
    BigDecimal totalPedidos;      // default ZERO
    BigDecimal limiteAprovado;    // pode ser ZERO se nunca definido
    List<ClienteRow> clientes;
}

class ClienteRow {
    Long clienteId;
    String razaoSocial;
    String cnpj;
    BigDecimal pendenteDep1;      // default ZERO
    BigDecimal pendenteDep510;    // default ZERO
    BigDecimal analisadoDep1;     // default ZERO
    BigDecimal analisadoDep510;   // default ZERO
    BigDecimal totalPedidos;      // default ZERO
    // SEM limiteAprovado — exibe "—" no template
    List<PedidoRow> pedidos;
}

class PedidoRow {
    Long pedidoId;
    String numero;
    LocalDate data;
    String condicaoPagamento;
    String deposito;
    BigDecimal valor;
    String status; // "Pendente" ou "Analisado"
}
```

Todos os campos `BigDecimal` devem ser inicializados como `BigDecimal.ZERO` para evitar NPE na renderização do template.

---

## Arquivos a Criar

| Arquivo | Tipo |
|---|---|
| `domain/model/HistoricoLimite.java` | Entidade JPA |
| `infrastructure/persistence/HistoricoLimiteRepository.java` | Spring Data Repository |
| `application/service/LimiteService.java` | Service `@Transactional` |
| `presentation/dto/RelatorioLimitesDTO.java` | DTO (3 inner classes) |
| `presentation/controller/RelatorioController.java` | MVC Controller |
| `templates/relatorio-limites.html` | Thymeleaf Template |
| `templates/fragments/historico-limite-modal.html` | Fragment HTMX |

## Arquivos a Modificar

| Arquivo | Alteração |
|---|---|
| `domain/model/GrupoEconomico.java` | Adicionar `@OneToMany List<HistoricoLimite> historicosLimite` |
| `templates/fragments/layout.html` | Adicionar link "Relatório" no menu de navegação |

---

## Abordagem de Implementação

- **Opção A** (escolhida): Tabela expandível com Bootstrap `collapse`. Toda a estrutura de dados é computada no servidor e renderizada pelo Thymeleaf. JavaScript nativo do Bootstrap controla expand/collapse. Sem novas dependências.
- O modal de edição de limite usa formulário HTML padrão (full page POST + redirect), sem HTMX, para evitar conflitos com o ciclo de vida do modal.
- O histórico de limites usa HTMX para carregar o fragment apenas quando o usuário clica em "Ver histórico" — lazy load pontual sem complexidade adicional.

---

## Fora de Escopo

- Export para Excel/CSV (pode ser adicionado depois)
- Paginação server-side (volume esperado cabe em memória para o MVP)
- Edição inline de outros campos além do limite
