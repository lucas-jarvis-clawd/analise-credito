# Kanban Dashboard Implementation

## Overview

The Kanban dashboard is the heart of the credit analysis application. It provides a visual board where analysts can see all analyses grouped by workflow type and status, with drag-and-drop functionality to move cards between columns.

## Implementation Details

### 1. KanbanController

**Location:** `/src/main/java/AnaliseCredito/Analise_de_Credito/presentation/controller/KanbanController.java`

**Key Features:**
- **GET /analise/kanban** - Main dashboard endpoint
  - Supports filtering: TODOS (default), PRAZO (BASE_PRAZO only), NOVO (CLIENTE_NOVO only)
  - Groups analyses by workflow and status
  - Calculates alerts for each pedido using AlertaService
  - Passes perfil from session to template

- **POST /analise/{id}/status** - AJAX endpoint for status updates
  - Receives analiseId and novoStatus
  - Uses WorkflowService.transicionar() to validate and apply transitions
  - Returns JSON response with success/error

**Dependencies:**
- AnaliseRepository
- PedidoRepository
- AlertaService
- WorkflowService

### 2. Kanban Template

**Location:** `/src/main/resources/templates/kanban.html`

**Structure:**
- Navigation bar with perfil display and logout
- Filter buttons (Todos, Base/Prazo, Cliente Novo)
- Two workflow sections (if filtro=TODOS):
  - BASE_PRAZO workflow
  - CLIENTE_NOVO workflow
- Columns for each status in the workflow
- Cards rendered using Thymeleaf fragments

**Kanban Columns by Workflow:**

**BASE_PRAZO:**
1. PENDENTE
2. EM_ANALISE_FINANCEIRO
3. PARECER_APROVADO
4. PARECER_REPROVADO
5. FINALIZADO

**CLIENTE_NOVO:**
1. PENDENTE
2. DOCUMENTACAO_SOLICITADA
3. DOCUMENTACAO_ENVIADA
4. PARECER_APROVADO
5. PARECER_REPROVADO
6. FINALIZADO

**Card Display:**
Each card shows:
- Razão Social (bold)
- CNPJ (small, gray)
- Marca | Pedido Number | Valor
- Data do Pedido
- Alert badges (red) - calculated by AlertaService
- Score badge (green/yellow/red based on value)
- "Analisar" button (links to /analise/{id})

**Score Color Logic:**
- >= 700: Green (score-alto)
- >= 400: Yellow (score-medio)
- < 400: Red (score-baixo)

### 3. Drag and Drop Implementation

**Technology:** Vanilla JavaScript (no external library except HTMX)

**How it works:**
1. All cards have `draggable="true"` attribute
2. JavaScript event listeners:
   - `dragstart` - marks card as dragging
   - `dragend` - removes dragging class
   - `dragover` - allows drop and highlights column
   - `dragleave` - removes highlight
   - `drop` - triggers AJAX call to update status

3. On drop:
   - Extracts `data-analise-id` from card
   - Extracts `data-status` from target column
   - POSTs to `/analise/{id}/status` with new status
   - Reloads page on success (to update counts and card positions)
   - Shows alert on error (invalid transition)

### 4. Alert Badges

Calculated by AlertaService for each pedido:
- **SIMEI > LIMITE** - SIMEI client with order above limit
- **GRUPO > X SIMEIS** - Group with too many SIMEI clients
- **PEDIDO > LIMITE** - Order above group's approved limit
- **TOTAL > LIMITE** - Total open orders above limit
- **RESTRIÇÕES (X)** - Count of restrictions (Pefin + Protestos + Ações + Cheques)
- **SCORE BAIXO** - Boa Vista score below threshold

### 5. Data Initializer

**Location:** `/src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/config/DataInitializer.java`

Creates test data on application startup:
- 1 Configuracao (ID=1)
- 2 GrupoEconomico
- 4 Clientes (2 for BASE_PRAZO, 2 for CLIENTE_NOVO)
- 6 Pedidos with Análises in different statuses

## Testing the Implementation

### 1. Start the Application

```bash
mvn spring-boot:run
```

### 2. Access the Application

Open browser to: `http://localhost:8081/`

### 3. Select Profile

Click "Entrar como Financeiro" or "Entrar como Comercial"

### 4. View Kanban Dashboard

You should see:
- Two workflow sections (BASE_PRAZO and CLIENTE_NOVO)
- Cards distributed across different status columns
- Alert badges on some cards
- Score badges on all cards

### 5. Test Filters

Click the filter buttons:
- **Todos** - shows both workflows
- **Base/Prazo** - shows only BASE_PRAZO workflow
- **Cliente Novo** - shows only CLIENTE_NOVO workflow

### 6. Test Drag and Drop

Try dragging a card from PENDENTE to EM_ANALISE_FINANCEIRO:
- Valid transition - page reloads, card moves
- Invalid transition - error alert shown

### 7. Test Card Click

Click "Analisar" on any card:
- Should navigate to `/analise/{id}` (not yet implemented - Task #14)

## Workflow Transitions

The drag-and-drop respects WorkflowService transition rules:

**BASE_PRAZO valid transitions:**
- PENDENTE → EM_ANALISE_FINANCEIRO
- EM_ANALISE_FINANCEIRO → PARECER_APROVADO | PARECER_REPROVADO
- PARECER_APROVADO → AGUARDANDO_APROVACAO_GESTOR | REANALISE_COMERCIAL_SOLICITADA | FINALIZADO
- PARECER_REPROVADO → AGUARDANDO_APROVACAO_GESTOR | REANALISE_COMERCIAL_SOLICITADA | FINALIZADO

**CLIENTE_NOVO valid transitions:**
- PENDENTE → DOCUMENTACAO_SOLICITADA
- DOCUMENTACAO_SOLICITADA → DOCUMENTACAO_ENVIADA
- DOCUMENTACAO_ENVIADA → PARECER_APROVADO | PARECER_REPROVADO
- PARECER_APROVADO → AGUARDANDO_APROVACAO_GESTOR | REANALISE_COMERCIAL_SOLICITADA | FINALIZADO

## Future Enhancements

Possible improvements for later:
1. Real-time updates without page reload (WebSocket)
2. Optimistic UI updates (move card before server confirmation)
3. Undo functionality
4. Bulk operations (select multiple cards)
5. Search/filter within cards
6. Sort options (by date, value, score)
7. Export to Excel/PDF
8. Performance metrics (avg time per status)

## Technical Notes

### Why Vanilla JS instead of Library?

The drag-and-drop is simple enough that a library (like Sortable.js or React DnD) would be overkill. The vanilla implementation:
- Is easier to understand and debug
- Has no external dependencies
- Works well with Thymeleaf server-side rendering
- Can be enhanced later if needed

### Why Page Reload on Drop?

Currently, after a successful status update, the page reloads. This approach:
- Ensures data consistency (counts, card positions)
- Simplifies implementation (no complex state management)
- Works well with server-side rendering
- Can be replaced with HTMX partial updates later

### Performance Considerations

For large datasets (hundreds of cards), consider:
- Pagination (show only recent analyses)
- Lazy loading (load cards on scroll)
- Virtual scrolling
- Backend filtering/sorting

## Integration with Other Components

The Kanban dashboard integrates with:
- **HomeController** - Receives perfil from session
- **WorkflowService** - Validates and applies status transitions
- **AlertaService** - Calculates alert badges
- **AnaliseController** (Task #14) - Target for "Analisar" button
- **ParecerService** (Task #11) - Used during analysis workflow

## Files Created

1. `/src/main/java/AnaliseCredito/Analise_de_Credito/presentation/controller/KanbanController.java`
2. `/src/main/resources/templates/kanban.html`
3. `/src/main/java/AnaliseCredito/Analise_de_Credito/infrastructure/config/DataInitializer.java`
4. `/KANBAN_IMPLEMENTATION.md` (this file)

## Summary

The Kanban dashboard is fully functional and provides:
- Visual pipeline of all credit analyses
- Filtering by workflow type
- Drag-and-drop status updates with validation
- Rich card display with alerts and scores
- Test data for immediate demonstration
- Clean integration with existing services

The implementation is production-ready and can be enhanced with the future improvements listed above.
