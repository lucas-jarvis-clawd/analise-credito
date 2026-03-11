# Design: Relatório de Limites por Grupo Econômico

**Data:** 2026-03-11
**Status:** Aprovado

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
public class HistoricoLimite {
    Long id;
    @ManyToOne GrupoEconomico grupoEconomico;  // required
    BigDecimal valor;                           // precision 15.2
    LocalDateTime dataRegistro;                // auto-set on create
    String responsavel;                         // analista que registrou (max 100)
}
```

### Alteração em `GrupoEconomico`

Adiciona relacionamento `@OneToMany(mappedBy = "grupoEconomico") List<HistoricoLimite> historicosLimite`.

O campo `limiteAprovado` (BigDecimal) permanece como fonte de verdade atual e é sempre igual ao valor do `HistoricoLimite` mais recente. Ao gravar um novo limite, atualiza-se ambos atomicamente na mesma transação.

---

## Regras de Negócio

### Campo `deposito` em `Pedido`

- Valores possíveis: `"1"`, `"5"`, `"10"`
- Agrupamento na tela: **Dep. 1** = valor `"1"` | **Dep. 5+10** = valores `"5"` e `"10"`

### Classificação de pedidos

- **Pendente de análise:** pedido sem análise finalizada (sem `Analise`, ou `Analise.statusWorkflow != FINALIZADO`)
- **Analisado:** pedido com `Analise.statusWorkflow == FINALIZADO`

### Colunas de valor por grupo/CNPJ

| Coluna | Descrição |
|---|---|
| Pendente Dep.1 | Soma de `Pedido.valor` dos pedidos pendentes com `deposito = "1"` |
| Pendente Dep.5+10 | Soma de `Pedido.valor` dos pedidos pendentes com `deposito` in `{"5", "10"}` |
| Analisado Dep.1 | Soma de `Pedido.valor` dos pedidos analisados com `deposito = "1"` |
| Analisado Dep.5+10 | Soma de `Pedido.valor` dos pedidos analisados com `deposito` in `{"5", "10"}` |
| Total Pedidos | Soma de todos os pedidos do grupo (todas as combinações acima) |
| Limite Aprovado | `GrupoEconomico.limiteAprovado` atual |

### Atualização de limite

- Disponível em qualquer tela que exiba o grupo (relatório e analise wizard)
- Ao salvar: (1) insere `HistoricoLimite`, (2) atualiza `GrupoEconomico.limiteAprovado` — tudo em uma única transação
- O responsável é pré-preenchido com o analista da sessão HTTP (`session.getAttribute("perfilUsuario")`)

---

## Tela: `/relatorio/limites`

### Barra de filtros

- Busca por nome ou CNPJ do grupo (texto livre, filtro server-side)
- Filtro por UF (select)

### Tabela hierárquica

#### Nível 1 — Grupo Econômico

Linha com fundo escuro (Bootstrap `table-dark` ou `table-primary`). Expansível via botão `▶/▼` que usa `data-bs-toggle="collapse"` do Bootstrap.

Colunas:
```
[▶] Nome do Grupo | Pend. Dep.1 | Pend. Dep.5+10 | Anal. Dep.1 | Anal. Dep.5+10 | Total Pedidos | Limite Aprovado | [✏️ Editar]
```

#### Nível 2 — CNPJ/Cliente

Linha indentada (fundo cinza, `table-secondary`). Expansível da mesma forma. Exibe razão social + CNPJ formatado. Mesmas colunas de valor que o nível 1.

#### Nível 3 — Pedido

Linha mais indentada (fundo branco). Sem botão de expansão. Colunas:

```
Número | Data | Condição Pagamento | Depósito | Valor | Status (Pendente/Analisado)
```

#### Rodapé totalizador

Linha `<tfoot>` bold com soma de todas as colunas de valor de todos os grupos visíveis.

### Modal: Editar Limite

Disparado pelo botão ✏️ na linha do grupo. Bootstrap modal com:
- Campo `valor` (número, obrigatório)
- Campo `responsavel` (texto, pré-preenchido, editável)
- Botão "Salvar" → `POST /relatorio/limites/{grupoId}/limite`
- Link "Ver histórico de limites" → abre segundo modal com tabela do histórico

### Modal: Histórico de Limites

Tabela simples com colunas: Data, Valor, Responsável. Dados carregados via `GET /relatorio/limites/{grupoId}/historico` (fragment HTMX inserido no modal).

---

## Controller: `RelatorioController`

```
GET  /relatorio/limites
     Params: busca (String), uf (String)
     → renderiza relatorio-limites.html com List<RelatorioLimitesDTO.GrupoRow>

POST /relatorio/limites/{grupoId}/limite
     Body: valor (BigDecimal), responsavel (String)
     → salva HistoricoLimite + atualiza GrupoEconomico.limiteAprovado
     → redirect para GET /relatorio/limites

GET  /relatorio/limites/{grupoId}/historico
     → retorna fragment HTML (historico-limite-modal.html) com lista de HistoricoLimite
```

---

## DTOs

### `RelatorioLimitesDTO`

```java
class GrupoRow {
    Long grupoId;
    String grupoNome;
    String grupoCodigo;
    BigDecimal pendenteDep1;
    BigDecimal pendenteDep510;
    BigDecimal analisadoDep1;
    BigDecimal analisadoDep510;
    BigDecimal totalPedidos;
    BigDecimal limiteAprovado;
    List<ClienteRow> clientes;
}

class ClienteRow {
    Long clienteId;
    String razaoSocial;
    String cnpj;
    BigDecimal pendenteDep1;
    BigDecimal pendenteDep510;
    BigDecimal analisadoDep1;
    BigDecimal analisadoDep510;
    BigDecimal totalPedidos;
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

---

## Arquivos a Criar

| Arquivo | Tipo |
|---|---|
| `domain/model/HistoricoLimite.java` | Entidade JPA |
| `infrastructure/persistence/HistoricoLimiteRepository.java` | Spring Data Repository |
| `presentation/dto/RelatorioLimitesDTO.java` | DTO (3 inner classes) |
| `presentation/controller/RelatorioController.java` | MVC Controller |
| `templates/relatorio-limites.html` | Thymeleaf Template |
| `templates/fragments/historico-limite-modal.html` | Fragment HTMX |

## Arquivos a Modificar

| Arquivo | Alteração |
|---|---|
| `domain/model/GrupoEconomico.java` | Adicionar `@OneToMany List<HistoricoLimite>` |
| `templates/fragments/layout.html` | Adicionar link "Relatório" no menu |

---

## Abordagem de Implementação

- **Opção A** (escolhida): Tabela expandível com Bootstrap `collapse`. Toda a estrutura de dados é computada no servidor e renderizada pelo Thymeleaf. JavaScript nativo do Bootstrap controla expand/collapse. Sem novas dependências.
- O histórico de limites usa HTMX para carregar o fragment apenas quando o usuário clica em "Ver histórico" — lazy load pontual sem complexidade adicional.

---

## Fora de Escopo

- Export para Excel/CSV (pode ser adicionado depois)
- Paginação server-side (volume esperado cabe em memória para o MVP)
- Edição inline de outros campos além do limite
