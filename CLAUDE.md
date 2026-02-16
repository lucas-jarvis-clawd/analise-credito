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

Na primeira execucao, `DataInitializer` popula dados de exemplo automaticamente (config, grupos, clientes, pedidos, analises) se o banco estiver vazio.

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
    ├── controller/     # HomeController, KanbanController, AnaliseController, ImportacaoController, ConfiguracaoController, DocumentoController
    └── dto/            # AnaliseForm, ImportacaoDTO
```

## Domain Model (Core Entities)

**GrupoEconomico** (SEMPRE existe - se cliente sem grupo, cria com codigo=cnpj)
- `limiteAprovado`, `limiteDisponivel` (SEMPRE no grupo, nunca no Cliente)
- 1:N -> Cliente, DadosBI

**Cliente**
- N:1 -> GrupoEconomico (OBRIGATORIO)
- 1:N -> Pedido, Documento, Duplicata, Socio, Participacao, Restricoes

**Pedido**
- `bloqueio` determina workflow: 80/36 = CLIENTE_NOVO, outros = BASE_PRAZO
- `colecao` (Integer) - usada na cross-tab de pedidos do grupo
- `alerts` (List<String>) - calculados dinamicamente por AlertaService
- N:1 -> Cliente, 1:1 -> Analise

**Analise**
- Referencias: pedidoId, clienteId, grupoEconomicoId
- `statusWorkflow` (enum), `tipoAnalista` (FINANCEIRO/COMERCIAL)
- `parecerCRM` gerado APENAS para workflow CLIENTE_NOVO
- `requerAprovacaoGestor` (boolean) - baseado em regras de alcada

**DadosBI** (por colecao, vinculado ao GrupoEconomico)
- `colecao` (Integer, ex: 202601), `credito`, `score` (interno), `valorVencido`, `atrasoMedio`

**Restricoes** (4 entidades: Pefin, Protesto, AcaoJudicial, Cheque)
- Inseridas MANUALMENTE pelo analista na aba Restricoes (nao vem de planilha)
- Cada registro = 1 ocorrencia individual com campos descritivos (origem, cartorio, banco, etc.)
- CRUD inline via POST endpoints no AnaliseController

**Duplicata**
- `getAtraso()` (metodo calculado, NAO coluna fixa)

**Configuracao** (tabela unica, 1 registro, editavel por admin)
- Limites SIMEI, thresholds score, multiplicadores, criterios alcada

## Workflows (Dois Distintos)

### BASE_PRAZO (bloqueio != 80 e != 36)
PENDENTE -> EM_ANALISE_FINANCEIRO -> PARECER_APROVADO/REPROVADO -> [AGUARDANDO_APROVACAO_GESTOR] -> [REANALISE_COMERCIAL] -> FINALIZADO

### CLIENTE_NOVO (bloqueio == 80 ou == 36)
PENDENTE -> DOCUMENTACAO_SOLICITADA -> DOCUMENTACAO_ENVIADA -> PARECER_APROVADO/REPROVADO -> [AGUARDANDO_APROVACAO_GESTOR] -> [REANALISE_COMERCIAL] -> FINALIZADO

**Diferenca critica:** CLIENTE_NOVO gera `parecerCRM` automaticamente.

## Key Business Rules

### 1. Grupo Economico Sempre Existe
Ao importar Cliente sem grupoEconomicoId, cria-se grupo com codigo = cnpj do cliente.

### 2. Calculo de Limite Sugerido (ScoringService)
1. Buscar ultimas 2 colecoes BI do grupo
2. Pegar maior credito entre as 2
3. Aplicar multiplicador por score interno: >=800: 1.5x | >=600: 1.2x | >=400: 1.0x | <400: 0.7x
4. Cap para SIMEI: se grupo tem SIMEI com pedido, max = limiteSimei

### 3. Sistema de Alertas (configuraveis)
- SIMEI > LIMITE: simei && pedido.valor > config.limiteSimei
- GRUPO > X SIMEIS: grupo tem > maxSimeisPorGrupo com pedidos
- PEDIDO > LIMITE: pedido.valor > grupo.limiteAprovado
- TOTAL > LIMITE: soma pedidos abertos > limite
- RESTRICOES (X): count(protestos + pefin + acoes + cheques) > 0
- SCORE BAIXO: scoreBoaVista < scoreBaixoThreshold

### 4. Regras de Alcada
```java
analise.requerAprovacaoGestor = (
  pedido.valor > valorAprovacaoGestor ||
  totalPedidosGrupo > totalGrupoAprovacaoGestor ||
  restricoesTotal >= restricoesAprovacaoGestor
)
```

## Wizard de Analise (7 tabs)

1. **Dados Cadastrais** - info do cliente (CNPJ, razao, fundacao, SIMEI, etc.)
2. **Pedidos Grupo** - cross-tab de pedidos por CNPJ x Marca com expand/collapse
3. **Vinculos e Socios** - socios e participacoes em outras empresas
4. **Restricoes** - PEFIN, Protestos, Acoes Judiciais, Cheques (input manual + delete)
5. **Financeiro** - DadosBI e Duplicatas em aberto
6. **Documentos** - upload/download de PDFs e imagens
7. **Historico** - timeline da analise

Painel lateral: Score, limites, formulario de decisao (Aprovado/Limitado/Reprovado + justificativa).

## Gotchas & Non-Obvious Patterns

1. **GrupoEconomico nunca e null** - Todo cliente TEM grupo (mesmo que seja so ele)
2. **Workflow determinado por bloqueio** - Campo `bloqueio` do pedido define qual workflow (80/36 = novo)
3. **Parecer CRM condicional** - So gera para CLIENTE_NOVO, nao para BASE_PRAZO
4. **Atraso calculado, nao armazenado** - Duplicata.getAtraso() e metodo, nao coluna
5. **Alertas dinamicos** - List<String> calculada on-the-fly, nao persistida
6. **Limite no grupo, nao no cliente** - Cliente.limiteAprovado NAO existe
7. **DadosBI por colecao** - Cada linha = 1 colecao de 1 grupo (nao por cliente)
8. **Restricoes manuais** - Nao vem de importacao, analista insere manualmente na aba
9. **Cross-tab pedidos** - Agrupamento CNPJ x Marca filtrado por colecao do pedido em analise
10. **Porta 8081** - Nao e a porta padrao do Spring Boot (8080)

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

## MVP Scope (What's NOT Included)

- Autenticacao (usar selecao manual de perfil)
- Integracao ERP (usar importacao XLSX)
- Replicar calculo BI (importar DadosBI.xlsx)
- API REST (apenas MVC)
- Oracle (usar H2 por enquanto)
