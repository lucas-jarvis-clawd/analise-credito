# Sistema de Analise de Credito

Webapp para digitalizar analise de credito de lojistas, substituindo planilhas Excel por interface Kanban interativa com dois workflows distintos (clientes base prazo vs novos/antecipados).

## Quick Start

```bash
# Build
mvn clean install

# Run (porta 8081, H2 console em http://localhost:8081/h2-console)
mvn spring-boot:run

# Test
mvn test
```

## Design System

**Skill:** `.claude/tex-cotton-design/SKILL.md` — invocar SEMPRE antes de criar ou editar qualquer template/UI
**CSS:** `texcotton-theme.css` v3.0 — DM Sans font, `--accent: #d4a843`, `--bg-main: #f5f5f0`
**Logo:** `.claude/tex-cotton-design/assets/LogoTex.png`
**Design specs:** `docs/superpowers/specs/` (arquivos markdown de especificação de telas)

## Architecture

**Stack:** Spring Boot 3.5.7 + Java 21 + Thymeleaf + HTMX + Bootstrap 5 + H2 (-> Oracle producao)

**Package Structure:**
```
AnaliseCredito.Analise_de_Credito/
├── domain/
│   ├── model/          # 14 entidades JPA
│   └── enums/          # TipoWorkflow, StatusWorkflow, TipoCliente, etc
├── application/
│   └── service/        # ScoringService, AlertaService, WorkflowService, ParecerService, ImportacaoService
├── infrastructure/
│   ├── persistence/    # 14 Spring Data Repositories
│   ├── storage/        # FileStorageService (uploads)
│   └── config/         # DataInitializer (seed data)
└── presentation/
    ├── controller/     # MVC Controllers
    └── dto/            # Form/View DTOs (inclui GrupoKanbanDTO para Kanban)
```

## Domain Model (Core Entities)

**GrupoEconomico** (SEMPRE existe - se cliente sem grupo, cria com codigo=cnpj)
- `limiteAprovado`, `limiteDisponivel` (SEMPRE no grupo, nunca no Cliente)
- 1:N -> Cliente, DadosBI

**Cliente**
- N:1 → GrupoEconomico (OBRIGATÓRIO)
- `instagram` (String, 100 chars, opcional) - handle ou URL do Instagram da loja
- 1:N → Pedido, Documento, Duplicata, Socio, Participacao, Restrições

**Pedido**
- `bloqueio` determina workflow: 80/36 = CLIENTE_NOVO, outros = BASE_PRAZO
- `colecao` (Integer) - usada na cross-tab de pedidos do grupo
- `alerts` (List<String>) - calculados dinamicamente por AlertaService
- `colecao` (Integer), `nomeColecao` (String), `tipoOperacao` (TipoOperacao), `pedidoSazonal` (boolean)
- N:1 → Cliente, 1:1 → Analise

**Analise**
- Referencias: pedidoId, clienteId, grupoEconomicoId
- `statusWorkflow` (enum), `tipoAnalista` (FINANCEIRO/COMERCIAL)
- `parecerCRM` gerado APENAS para workflow CLIENTE_NOVO
- `requerAprovacaoGestor` (boolean) - baseado em regras de alçada
- `valorAprovado`, `prazoAprovado`, `condicoesEspeciais` - aprovação condicional
- `exigeGarantia` (boolean), `garantiaExigida` (TipoGarantia), `observacoesCondicoes`

**DadosBI** (por coleção, vinculado ao GrupoEconomico)
- `colecao` (Integer, ex: 202601), `credito`, `score` (**Score Tex** — interno Tex Cotton), `valorVencido`, `atrasoMedio`
- `getVariacaoScore()`, `getVariacaoAtraso()`, `getTendencia()` — @Transient, calculados comparando coleções consecutivas

**Restricoes** (4 entidades: Pefin, Protesto, AcaoJudicial, Cheque)
- Inseridas MANUALMENTE pelo analista na aba Restricoes (nao vem de planilha)
- Cada registro = 1 ocorrencia individual com campos descritivos (origem, cartorio, banco, etc.)
- CRUD inline via POST endpoints no AnaliseController

**Configuracao** (tabela única, 1 registro, editável por admin)
- Limites SIMEI, thresholds score, multiplicadores, critérios alçada
- 5 campos sazonalidade: multiplicadores e meses de alta temporada

**Enums adicionais:** `TipoOperacao` (REPOSICAO/LANCAMENTO/OPORTUNIDADE), `TipoGarantia` (6 tipos), `TendenciaRisco` (MELHORANDO/ESTAVEL/DETERIORANDO)

## Workflows (Dois Distintos)

### BASE_PRAZO (bloqueio != 80 e != 36)
PENDENTE → EM_ANALISE_FINANCEIRO → PARECER_APROVADO/REPROVADO/APROVADO_CONDICIONAL → [AGUARDANDO_APROVACAO_GESTOR] → [AGUARDANDO_ACEITE_CLIENTE] → [REANALISE_COMERCIAL] → FINALIZADO

### CLIENTE_NOVO (bloqueio == 80 ou == 36)
PENDENTE → DOCUMENTACAO_SOLICITADA → DOCUMENTACAO_ENVIADA → PARECER_APROVADO/REPROVADO/APROVADO_CONDICIONAL → [AGUARDANDO_APROVACAO_GESTOR] → [AGUARDANDO_ACEITE_CLIENTE] → [REANALISE_COMERCIAL] → FINALIZADO

**Diferença crítica:** CLIENTE_NOVO gera `parecerCRM` automaticamente (formato 8 linhas: DECISÃO | CADASTRO+SCORE | HISTÓRICO | EXPOSIÇÃO | ALERTAS | APROVAÇÃO | FUNDAMENTAÇÃO | RESPONSÁVEL)

## Kanban Grouping Pattern

**IMPORTANTE:** Kanban agrupa por **GrupoEconomico**, NÃO por pedidos individuais.

**1 card = 1 grupo econômico** (pode ter múltiplos pedidos/clientes)

**Card mostra (GrupoKanbanDTO):**
- Nome/código do grupo
- Lista de todos os clientes do grupo
- Quantidade total de pedidos
- Valor total consolidado
- Pior score entre os clientes (badge colorido)
- Alertas consolidados de todos os pedidos
- Botão "Analisar" → abre análise principal (pedido mais antigo)

**Lógica de agrupamento (KanbanController):**
```java
// Agrupa análises por GrupoEconomico.id
// Status do card = status da análise mais antiga do grupo
// analisePrincipalId = ID da análise mais antiga (para botão Analisar)
```

## Key Business Rules

### 1. Grupo Economico Sempre Existe
Ao importar Cliente sem grupoEconomicoId, cria-se grupo com codigo = cnpj do cliente.

### 2. Calculo de Limite Sugerido (ScoringService)
1. Buscar ultimas 2 colecoes BI do grupo
2. Pegar maior credito entre as 2
3. Aplicar multiplicador por score interno: >=800: 1.5x | >=600: 1.2x | >=400: 1.0x | <400: 0.7x
4. Cap para SIMEI: se grupo tem SIMEI com pedido, max = limiteSimei

### 3. Sistema de Alertas (configuráveis)
- 🔴 **SIMEI > LIMITE**: simei && pedido.valor > config.limiteSimei
- 🔴 **GRUPO > X SIMEIS**: grupo tem > maxSimeisPorGrupo com pedidos
- ⚠️ **PEDIDO > LIMITE**: pedido.valor > grupo.limiteAprovado
- ⚠️ **TOTAL > LIMITE**: soma pedidos abertos > limite
- 🟡 **RESTRIÇÕES (X)**: count(protestos + pefin + ações + cheques) > 0
- 🟡 **SCORE BAIXO**: scoreBoaVista (Score Boa Vista) < scoreBaixoThreshold
- 🟡 **PEDIDO ACIMA SAZONALIDADE**: valor > 1.5x média histórica da coleção
- 🟡 **DETERIORAÇÃO SCORE / ATRASO CRESCENTE**: baseados em getTendencia() do DadosBI

### 4. Regras de Alcada
```java
analise.requerAprovacaoGestor = (
  pedido.valor > valorAprovacaoGestor ||
  totalPedidosGrupo > totalGrupoAprovacaoGestor ||
  restricoesTotal >= restricoesAprovacaoGestor
)
```

## Wizard de Analise (7 tabs)

**Status:** ✅ **95% COMPLETE - MVP FUNCIONAL COM 5 MELHORIAS CRÍTICAS!**

**Phases:**
1. ✅ Fundação: Pacotes, enums (6→9), entities (14), repos (14), config
2. ✅ Importação: ImportacaoService (Apache POI), XLSX parsing - 684 linhas, 14 testes
3. ✅ Services Core: Scoring (9 tests), Alertas (13 tests), Workflow (20 tests), Parecer (20 tests)
4. ✅ UI Kanban: Dashboard com drag-and-drop, filtros UF/valor/SLA/busca, badges SLA dinâmicos
5. ✅ Wizard Análise: 7 tabs + painel decisão + tendência BI + sazonalidade
6. ✅ CRUD/Admin: Home, Importação, Configuração, FileStorage, Documentos
7. ✅ Edição Manual: Financeiro pode editar score e adicionar/remover restrições
8. ✅ Kanban Agrupado: Cards agrupados por GrupoEconomico com informações consolidadas
9. ✅ 5 Melhorias: Filtros Kanban, Parecer 8-linhas, Sazonalidade, Aprovação Condicional, Tendência
10. ⏳ Testes: 119 testes unitários passando, E2E pendente
11. ⏳ Deploy: Build scripts, perfil produção Oracle

**Task List:**
- ✅ Tasks #1-17: Backend completo + Controllers + UI
- ✅ Tasks #18+: 5 melhorias críticas implementadas (filtros, parecer, sazonalidade, condicional, tendência)
- ⏳ E2E e deploy Oracle

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

### Manual Data Editing (Financeiro)

Financeiro pode editar manualmente dados do cliente na tela de análise:

**Score Boa Vista:**
- PUT `/analise/{id}/cliente/score` - atualizar scoreBoaVista

**Restrições (adicionar/remover individualmente):**
- POST `/analise/{id}/cliente/restricoes/pefin` - adicionar Pefin
- POST `/analise/{id}/cliente/restricoes/protesto` - adicionar Protesto
- POST `/analise/{id}/cliente/restricoes/acao` - adicionar Ação Judicial
- POST `/analise/{id}/cliente/restricoes/cheque` - adicionar Cheque
- DELETE `/restricoes/{tipo}/{id}` - remover restrição

**Instagram:**
- POST `/analise/{id}/cliente/instagram` - atualizar Instagram do cliente

**Localização UI:** Aba "Restrições" + "Dados Cadastrais" na tela de análise

## Gotchas & Non-Obvious Patterns

1. **GrupoEconomico nunca e null** - Todo cliente TEM grupo (mesmo que seja so ele)
2. **Workflow determinado por bloqueio** - Campo `bloqueio` do pedido define qual workflow (80/36 = novo)
3. **Parecer CRM condicional** - Só gera para CLIENTE_NOVO, não para BASE_PRAZO
4. **Atraso calculado, não armazenado** - Duplicata.getAtraso() é método getter, não coluna
5. **Alertas dinâmicos** - List<String> calculada on-the-fly, não persistida
6. **Limite no grupo, não no cliente** - Cliente.limiteAprovado NÃO existe
7. **HTMX para Kanban** - Drag-and-drop sem JavaScript pesado
8. **DadosBI por coleção** - Cada linha = 1 coleção de 1 grupo (não por cliente)
9. **Kanban agrupado por grupo** - 1 card = 1 GrupoEconomico (não 1 pedido). Drag-and-drop usa analisePrincipalId
10. **GrupoKanbanDTO no Kanban** - Template recebe List<GrupoKanbanDTO>, não List<Analise>
11. **Edição manual sempre disponível** - Score e restrições editáveis em qualquer status do workflow
12. **Filtros Kanban por URL params** - UF, faixa de valor, SLA, busca; estado preservado em query string
13. **Tendência DadosBI é @Transient** - getTendencia() compara última vs penúltima coleção, NÃO persiste
14. **Design system via skill** - Usar `.claude/tex-cotton-design` ANTES de criar/editar qualquer template HTML

## Configuration

### application.properties
```properties
server.port=8081
spring.datasource.url=jdbc:h2:mem:analisedb
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=create-drop

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

**Já adicionados:**
- Apache POI (XLSX parsing)
- HTMX webjar
- Bootstrap 5 webjar
- spring-boot-starter-validation

## MVP Scope (What's NOT Included)

- Autenticacao (usar selecao manual de perfil)
- Integracao ERP (usar importacao XLSX)
- Replicar calculo BI (importar DadosBI.xlsx)
- API REST (apenas MVC)
- Oracle (usar H2 por enquanto)
