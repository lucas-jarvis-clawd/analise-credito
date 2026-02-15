# Sistema de Análise de Crédito

Webapp para digitalizar análise de crédito de lojistas, substituindo planilhas Excel por interface Kanban interativa com dois workflows distintos (clientes base prazo vs novos/antecipados).

## Quick Start

```bash
# Build
./mvnw clean install

# Run (H2 console at http://localhost:8080/h2-console)
./mvnw spring-boot:run

# Test
./mvnw test
```

## Architecture

**Stack:** Spring Boot 4.0.2 + Java 25 + Thymeleaf + HTMX + Bootstrap 5 + H2 (→ Oracle produção)

**Package Structure:**
```
AnaliseCredito.Analise_de_Credito/
├── domain/
│   ├── model/          # Entidades JPA
│   ├── enums/          # TipoWorkflow, StatusWorkflow, TipoCliente, etc
│   └── valueobjects/   # CNPJ, Score
├── application/
│   └── service/        # ScoringService, AlertaService, WorkflowService, ParecerService
├── infrastructure/
│   ├── persistence/    # Spring Data Repositories
│   └── storage/        # FileStorageService (uploads)
└── presentation/
    ├── controller/     # MVC Controllers
    └── dto/            # Form/View DTOs
```

## Domain Model (Core Entities)

**GrupoEconomico** (SEMPRE existe - se cliente sem grupo, cria com codigo=cnpj)
- `limiteAprovado`, `limiteDisponivel` (SEMPRE no grupo, nunca no Cliente)
- 1:N → Cliente, DadosBI

**Cliente**
- N:1 → GrupoEconomico (OBRIGATÓRIO)
- 1:N → Pedido, Documento, Duplicata, Socio, Participacao, Restrições

**Pedido**
- `bloqueio` determina workflow: 80/36 = CLIENTE_NOVO, outros = BASE_PRAZO
- `alerts` (List<String>) - calculados dinamicamente por AlertaService
- N:1 → Cliente, 1:1 → Analise

**Analise**
- Referências: pedidoId, clienteId, grupoEconomicoId
- `statusWorkflow` (enum), `tipoAnalista` (FINANCEIRO/COMERCIAL)
- `parecerCRM` gerado APENAS para workflow CLIENTE_NOVO
- `requerAprovacaoGestor` (boolean) - baseado em regras de alçada

**DadosBI** (por coleção, vinculado ao GrupoEconomico)
- `colecao` (Integer, ex: 202601), `credito`, `score` (interno), `valorVencido`, `atrasoMedio`

**Duplicata**
- `getAtraso()` (método calculado, NÃO coluna fixa):
  ```java
  if (vencimento < today) {
    return (dataPagamento != null)
      ? dataPagamento - vencimento
      : today - vencimento
  }
  return 0
  ```

**Configuracao** (tabela única, 1 registro, editável por admin)
- Limites SIMEI, thresholds score, multiplicadores, critérios alçada

## Workflows (Dois Distintos)

### BASE_PRAZO (bloqueio != 80 e != 36)
PENDENTE → EM_ANALISE_FINANCEIRO → PARECER_APROVADO/REPROVADO → [AGUARDANDO_APROVACAO_GESTOR] → [REANALISE_COMERCIAL] → FINALIZADO

### CLIENTE_NOVO (bloqueio == 80 ou == 36)
PENDENTE → DOCUMENTACAO_SOLICITADA → DOCUMENTACAO_ENVIADA → PARECER_APROVADO/REPROVADO → [AGUARDANDO_APROVACAO_GESTOR] → [REANALISE_COMERCIAL] → FINALIZADO

**Diferença crítica:** CLIENTE_NOVO gera `parecerCRM` automaticamente (formato: "[DECISÃO] DATA - TIPO - FUNDAÇÃO - SIMEI - RESTRIÇÕES - CRED - SCORE - SÓCIOS - PARTS")

## Key Business Rules

### 1. Grupo Econômico Sempre Existe
```java
// Ao importar Cliente sem grupoEconomicoId:
if (grupoEconomicoId == null) {
  grupoEconomico = new GrupoEconomico(codigo: cliente.cnpj)
}
```

### 2. Cálculo de Limite Sugerido (ScoringService)
```java
// 1. Buscar últimas 2 coleções BI do grupo
// 2. Pegar maior crédito entre as 2
// 3. Aplicar multiplicador por score interno:
//    >= 800: 1.5x | >= 600: 1.2x | >= 400: 1.0x | < 400: 0.7x
// 4. Cap para SIMEI: se grupo tem SIMEI com pedido, max = limiteSimei
```

### 3. Sistema de Alertas (configuráveis)
- 🔴 **SIMEI > LIMITE**: simei && pedido.valor > config.limiteSimei
- 🔴 **GRUPO > X SIMEIS**: grupo tem > maxSimeisPorGrupo com pedidos
- ⚠️ **PEDIDO > LIMITE**: pedido.valor > grupo.limiteAprovado
- ⚠️ **TOTAL > LIMITE**: soma pedidos abertos > limite
- 🟡 **RESTRIÇÕES (X)**: count(protestos + pefin + ações + cheques) > 0
- 🟡 **SCORE BAIXO**: scoreBoaVista < scoreBaixoThreshold

### 4. Regras de Alçada
```java
analise.requerAprovacaoGestor = (
  pedido.valor > valorAprovacaoGestor ||
  totalPedidosGrupo > totalGrupoAprovacaoGestor ||
  restricoesTotal >= restricoesAprovacaoGestor
)
```

## Implementation Plan

**Status:** In execution (using executing-plans skill with parallel agents)

**Phases:**
1. ✅ Fundação: Pacotes, enums, entities, repos, config
2. ⏳ Importação: ImportacaoService (Apache POI), XLSX parsing
3. ⏳ Services Core: Scoring, Alertas, Workflow, Parecer
4. ⏳ UI Kanban: Dashboard com HTMX drag-and-drop
5. ⏳ Wizard Análise: 4 steps + painel decisão lateral
6. ⏳ CRUD/Admin: Cliente, Documentos, Configuração
7. ⏳ Testes: Unit tests (services) + acceptance (E2E)
8. ⏳ Deploy: Docs, build scripts, perfil produção

**Execution Strategy:**
- Batches de 3 tarefas com review entre batches
- Agentes paralelos para tarefas independentes (services)
- Sequencial para dependências (entities → repos → services → controllers)

**Task List:** 20 tarefas totais (ver TaskList para status atual)

## Development Workflow

### Task Execution
```bash
# Ver tarefas pendentes
# Use TaskList tool in Claude

# Marcar tarefa em progresso
# Use TaskUpdate tool with status: in_progress

# Marcar tarefa concluída
# Use TaskUpdate tool with status: completed
```

### Testing Strategy
- **Unit tests:** Services principais (Scoring, Alertas, Parecer, Duplicata.getAtraso())
- **Acceptance tests:** Importação, Kanban drag-drop, Wizard, Workflows, Alçada
- **Verification:** H2 Console para validar dados importados

### File Upload
- Diretório: `/static/uploads/{cnpj}/`
- Tipos permitidos: PDF, imagens
- Max size: configurável em application.properties

## Gotchas & Non-Obvious Patterns

1. **GrupoEconomico nunca é null** - Todo cliente TEM grupo (mesmo que seja só ele)
2. **Workflow determinado por bloqueio** - Campo `bloqueio` do pedido define qual workflow (80/36 = novo)
3. **Parecer CRM condicional** - Só gera para CLIENTE_NOVO, não para BASE_PRAZO
4. **Atraso calculado, não armazenado** - Duplicata.getAtraso() é método getter, não coluna
5. **Alertas dinâmicos** - List<String> calculada on-the-fly, não persistida
6. **Limite no grupo, não no cliente** - Cliente.limiteAprovado NÃO existe
7. **HTMX para Kanban** - Drag-and-drop sem JavaScript pesado
8. **DadosBI por coleção** - Cada linha = 1 coleção de 1 grupo (não por cliente)

## Configuration

### application.properties (H2)
```properties
spring.datasource.url=jdbc:h2:mem:analisedb
spring.h2.console.enabled=true
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=create-drop

# File upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
upload.path=/static/uploads/
```

### Future: Oracle Migration
- Adicionar driver Oracle JDBC ao pom.xml
- Criar perfil application-prod.properties
- Mudar ddl-auto para validate (produção)

## Dependencies (pom.xml)

**Current:**
- spring-boot-starter-data-jpa
- spring-boot-starter-thymeleaf
- spring-boot-starter-webmvc
- h2
- spring-boot-devtools

**To Add (Task #5):**
- Apache POI (XLSX parsing)
- HTMX webjar
- Bootstrap 5 webjar
- spring-boot-starter-validation
- lombok (optional)

## MVP Scope (What's NOT Included)

- ❌ Autenticação (usar seleção manual de perfil)
- ❌ Integração ERP (usar importação XLSX)
- ❌ Replicar cálculo BI (importar DadosBI.xlsx)
- ❌ API REST (apenas MVC)
- ❌ Oracle (usar H2 por enquanto)

**Roadmap pós-MVP:** Migração Oracle → Integração ERP → Auth AD/SSO → Cálculo BI interno → API REST/Mobile
