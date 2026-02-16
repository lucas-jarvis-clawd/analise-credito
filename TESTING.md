# Manual E2E Testing Guide

This document provides comprehensive manual testing scenarios for the Credit Analysis System. While automated integration tests cover the core functionality, these manual scenarios help verify the complete user experience including UI interactions, visual feedback, and edge cases.

## Prerequisites

1. Start the application: `mvn spring-boot:run`
2. Access: http://localhost:8081
3. H2 Console (if needed): http://localhost:8081/h2-console
   - JDBC URL: `jdbc:h2:mem:analise_credito`
   - Username: `sa`
   - Password: (leave blank)

## Test Data

The application auto-loads test data on startup (via DataInitializer):
- 2 Economic Groups
- 4 Clients (2 BASE_PRAZO, 2 CLIENTE_NOVO)
- 6 Orders with Analyses in various states
- 1 System Configuration

---

## Test Scenario 1: Profile Selection and Home Flow

**Objective:** Verify user can select profile and access the system

### Steps:
1. Open http://localhost:8081/
2. Verify home page displays with two profile buttons:
   - FINANCEIRO (Financial Analyst)
   - COMERCIAL (Commercial Analyst)
3. Click "FINANCEIRO" button
4. Verify redirect to Kanban dashboard at `/analise/kanban`
5. Verify session persists by navigating to other pages
6. Return to home and select "COMERCIAL"
7. Verify profile changes in Kanban header

### Expected Results:
- Clean, professional home page
- Smooth transition to Kanban
- Profile badge visible in navbar
- No errors in browser console

---

## Test Scenario 2: Kanban Dashboard Visualization

**Objective:** Verify Kanban displays all analysis cards correctly

### Steps:
1. Navigate to `/analise/kanban`
2. Verify two workflow sections are visible:
   - **BASE_PRAZO** (Existing Customers)
   - **CLIENTE_NOVO** (New Customers)
3. For each section, verify columns exist:
   - BASE_PRAZO: PENDENTE, EM_ANALISE_FINANCEIRO, PARECER_APROVADO, PARECER_REPROVADO
   - CLIENTE_NOVO: PENDENTE, DOCUMENTACAO_SOLICITADA, DOCUMENTACAO_ENVIADA, EM_ANALISE_FINANCEIRO
4. Verify each card displays:
   - Order number
   - Client name (CNPJ)
   - Order value
   - Alert badges (if any)
   - Days in current status
5. Verify alert badges show correct colors:
   - Red: Critical issues
   - Yellow: Warnings
   - Blue: Information

### Expected Results:
- All 6 test orders visible
- Cards properly distributed across workflow columns
- Alerts display for orders with restrictions, low scores, etc.
- Responsive layout works on different screen sizes

---

## Test Scenario 3: Kanban Filtering

**Objective:** Test workflow filter functionality

### Steps:
1. On Kanban, verify filter dropdown shows:
   - TODOS (All)
   - BASE_PRAZO (Existing Customers)
   - CLIENTE_NOVO (New Customers)
2. Select "BASE_PRAZO"
3. Verify only BASE_PRAZO section shows with 3 orders
4. Select "CLIENTE_NOVO"
5. Verify only CLIENTE_NOVO section shows with 3 orders
6. Select "TODOS"
7. Verify both sections return

### Expected Results:
- Filter updates URL parameter (?filtro=PRAZO)
- Page refreshes showing filtered data
- No JavaScript errors
- Active filter highlighted in UI

---

## Test Scenario 4: Kanban Drag-and-Drop (HTMX)

**Objective:** Verify drag-and-drop status updates work

### Steps:
1. Find a PENDENTE card in BASE_PRAZO section
2. Drag the card to "EM_ANALISE_FINANCEIRO" column
3. Verify card moves smoothly
4. Verify success message appears (if implemented)
5. Refresh page and verify card remains in new column
6. Try dragging to an invalid status (e.g., skip a required step)
7. Verify error message appears

### Expected Results:
- Visual feedback during drag (cursor changes, card highlights)
- HTMX request updates backend
- Page doesn't fully reload
- Invalid transitions prevented with error message
- Audit log updated with transition

---

## Test Scenario 5: Complete BASE_PRAZO Analysis

**Objective:** Walk through complete analysis for existing customer

### Steps:
1. From Kanban, click on "PED-001" (BASE_PRAZO order)
2. Verify wizard opens with 6 tabs:
   - Cadastrais (Client Info)
   - Vínculos (Relationships)
   - Restrições (Restrictions)
   - Financeiro (Financial)
   - Documentos (Documents)
   - Histórico (History)
3. Navigate through each tab:
   - **Cadastrais:** Verify client data, CNPJ, score, etc.
   - **Vínculos:** Check shareholders and participations
   - **Restrições:** Review PEFIN, protests, lawsuits
   - **Financeiro:** View BI data and receivables
   - **Documentos:** Check uploaded documents
   - **Histórico:** Review previous analyses
4. Verify right sidebar shows:
   - Current score
   - Suggested limit
   - Economic group limit
   - Decision buttons
5. Fill decision form:
   - Select "APROVADO"
   - Enter justification: "Cliente com bom histórico e score adequado"
6. Click "Concluir Análise"
7. Verify redirect to Kanban
8. Verify order moved to "PARECER_APROVADO" column
9. Verify success message displayed

### Expected Results:
- All tabs load without errors
- Data displays correctly formatted
- Score calculation accurate
- Limit suggestion reasonable
- Decision saves successfully
- Workflow transitions correctly

---

## Test Scenario 6: Complete CLIENTE_NOVO Analysis

**Objective:** Analyze a new customer with full documentation workflow

### Steps:
1. Click on "PED-004" (CLIENTE_NOVO order, SIMEI client)
2. Verify special alerts for new customer:
   - SIMEI indicator
   - Low score warning
   - Missing documentation
3. In "Restrições" tab:
   - Note any PEFIN/protests
   - Calculate total restrictions
4. In "Documentos" tab:
   - Verify required documents list
   - Check for missing documents
5. Make decision:
   - Select "LIMITADO"
   - Set limit: R$ 10.000,00
   - Justification: "Cliente novo SIMEI - limite inicial reduzido para teste"
6. Click "Concluir Análise"
7. Verify parecer CRM preview appears
8. Verify parecer includes:
   - Client summary
   - Score analysis
   - Restriction summary
   - Decision and justification
   - Analyst name
9. Confirm and complete
10. Verify economic group limit updated

### Expected Results:
- CLIENTE_NOVO workflow displays correctly
- SIMEI restrictions checked (max 3 per group, R$ 50k limit)
- Parecer CRM generated with all sections
- Economic group limit updated with approved amount
- Email notification sent (if configured)

---

## Test Scenario 7: Analysis Rejection

**Objective:** Test rejection workflow and messaging

### Steps:
1. Open "PED-002" (client with score 450 - low)
2. Review "Restrições" tab
3. Make decision:
   - Select "REPROVADO"
   - Justification: "Score muito baixo (450) e múltiplas restrições financeiras"
4. Complete analysis
5. Verify order moved to "PARECER_REPROVADO"
6. Verify economic group limit NOT updated
7. Verify rejection message clear

### Expected Results:
- Rejection processed correctly
- No limit granted (R$ 0,00)
- Economic group unaffected
- Status = PARECER_REPROVADO
- Clear audit trail

---

## Test Scenario 8: Manager Approval Flow

**Objective:** Test high-value order requiring manager approval

### Steps:
1. Go to Configuration page (`/configuracao`)
2. Set "Valor para Aprovação de Gestor": R$ 50.000,00
3. Save configuration
4. Create mental note that PED-005 = R$ 45.000,00 (above threshold)
5. Analyze PED-005
6. Make decision: "APROVADO"
7. Verify workflow transitions to "AGUARDANDO_APROVACAO_GESTOR"
8. Simulate manager login (change session profile)
9. Manager approves/rejects
10. Verify final status updated

### Expected Results:
- Analysis detects value > threshold
- Automatically requires manager approval
- Proper status transition
- Manager can review and decide
- Final status reflects manager decision

---

## Test Scenario 9: Document Upload

**Objective:** Test file upload functionality

### Steps:
1. Navigate to `/documentos` (or within analysis wizard)
2. Click "Upload Documento"
3. Select document type: "Contrato Social"
4. Choose file (PDF, JPG, PNG - max 10MB)
5. Enter description
6. Upload file
7. Verify file appears in documents list
8. Verify file stored in filesystem
9. Click download link
10. Verify file downloads correctly
11. Try uploading invalid file type (.exe)
12. Verify error message

### Expected Results:
- File upload progress indicator
- Success message after upload
- File listed with metadata
- Download works correctly
- Invalid files rejected
- File size limit enforced

---

## Test Scenario 10: Configuration Management

**Objective:** Test admin configuration panel

### Steps:
1. Navigate to `/configuracao`
2. Verify current configuration values displayed
3. Update each field:
   - **Limite SIMEI:** R$ 40.000,00
   - **Max SIMEIs por Grupo:** 5
   - **Valor Aprovação Gestor:** R$ 150.000,00
   - **Total Grupo Aprovação Gestor:** R$ 600.000,00
   - **Restrições Aprovação Gestor:** 5
   - **Score Baixo Threshold:** 350
4. Click "Salvar"
5. Verify success message
6. Refresh page
7. Verify new values persisted
8. Run an analysis to verify rules applied
9. Check that SIMEI limit now enforced at R$ 40k

### Expected Results:
- All fields editable
- Validation prevents invalid values (negative numbers)
- Changes save to database
- New rules immediately effective
- No application restart required

---

## Test Scenario 11: File Import (XLSX)

**Objective:** Test batch import of 4 XLSX files

### Steps:
1. Navigate to `/importacao`
2. Verify upload form with 4 file inputs:
   - Clientes.xlsx
   - Pedidos.xlsx
   - DadosBI.xlsx
   - Duplicatas.xlsx
3. Prepare test XLSX files with sample data
4. Upload all 4 files
5. Click "Processar Importação"
6. Verify progress indicator (if implemented)
7. Wait for completion
8. Verify result page shows:
   - Number of clients imported
   - Number of orders imported
   - Number of BI records
   - Number of receivables
   - Any errors/warnings
9. Navigate to Kanban
10. Verify new orders appear
11. Check H2 console to verify data in tables

### Expected Results:
- All 4 files required (validation)
- Only XLSX files accepted
- Import processes without errors
- Data correctly mapped to entities
- Foreign keys resolved (Cliente → GrupoEconomico)
- Duplicate handling (update vs. insert)
- Result summary accurate

---

## Test Scenario 12: Responsive Design

**Objective:** Verify UI works on different screen sizes

### Steps:
1. Open application in Chrome DevTools
2. Test at different breakpoints:
   - Mobile: 375px (iPhone SE)
   - Tablet: 768px (iPad)
   - Desktop: 1920px
3. Verify on each size:
   - Kanban columns stack appropriately
   - Navigation menu collapses to hamburger
   - Forms remain usable
   - Tables scroll horizontally if needed
   - Buttons/cards sized appropriately
4. Test on real mobile device if available

### Expected Results:
- Bootstrap 5 responsive classes work
- No horizontal scroll on mobile
- Touch targets large enough (min 44px)
- Text readable without zoom
- All features accessible

---

## Test Scenario 13: Error Handling

**Objective:** Verify graceful error handling

### Test Cases:

### A. Invalid Analysis ID
1. Navigate to `/analise/99999`
2. Verify 404 error page displays
3. Verify error message user-friendly

### B. Missing Required Fields
1. Try completing analysis without justification
2. Verify validation error message
3. Form retains entered data

### C. Database Connection Error
1. Stop H2 database (in test environment)
2. Try accessing Kanban
3. Verify error page displays with helpful message

### D. File Upload Error
1. Try uploading 50MB file
2. Verify size limit error
3. Try uploading .exe file
4. Verify file type error

### Expected Results:
- All errors caught gracefully
- User-friendly error messages
- No stack traces visible to users
- Errors logged for debugging
- User can recover without losing work

---

## Test Scenario 14: Alerts and Business Rules

**Objective:** Verify all business rule alerts function

### Alert Tests:

1. **Low Score Alert**
   - Find client with score < 400
   - Verify red "Score Baixo" badge

2. **SIMEI Limit Alert**
   - Find SIMEI order > R$ 50k
   - Verify "Valor acima do limite SIMEI" alert

3. **Group SIMEI Count Alert**
   - Create 4th SIMEI in same group
   - Verify "Grupo já possui 3 SIMEIs" alert

4. **High Value Alert**
   - Find order > R$ 100k
   - Verify "Requer aprovação do gestor" alert

5. **Multiple Restrictions Alert**
   - Find client with > 3 restrictions
   - Verify "Cliente com múltiplas restrições" alert

### Expected Results:
- All alerts calculate correctly
- Colors match severity (red/yellow/blue)
- Alert text clear and actionable
- Alerts update after configuration changes

---

## Test Scenario 15: Audit Trail and History

**Objective:** Verify system tracks all changes

### Steps:
1. Complete an analysis
2. Navigate to order history
3. Verify logged:
   - Who made decision
   - When decision made
   - Previous status
   - New status
   - Justification
4. Change configuration
5. Verify configuration history tracked
6. Update economic group limit
7. Verify limit change history

### Expected Results:
- All critical actions logged
- Timestamps accurate
- User attribution correct
- History immutable (cannot be deleted)
- Audit log filterable/searchable

---

## Test Scenario 16: Parecer CRM Generation

**Objective:** Verify CRM report generation for new customers

### Steps:
1. Analyze CLIENTE_NOVO order
2. In wizard, click "Visualizar Parecer" (if available)
3. Verify parecer preview shows:
   - Header with client info
   - Score analysis section
   - Restrictions summary
   - Financial analysis
   - Decision and recommendation
   - Analyst signature
4. Complete analysis
5. Download parecer as PDF (if implemented)
6. Verify PDF formatting professional

### Expected Results:
- Parecer generates automatically for CLIENTE_NOVO
- All sections populated
- Calculations accurate
- Professional formatting
- Ready for CRM system export

---

## Test Scenario 17: Concurrent Users

**Objective:** Test multi-user scenarios

### Steps:
1. Open application in two browsers (Chrome + Firefox)
2. Login as FINANCEIRO in Browser 1
3. Login as COMERCIAL in Browser 2
4. Both users view same Kanban
5. Browser 1 moves card to new status
6. Browser 2 refreshes
7. Verify Browser 2 sees update
8. Both users try editing same analysis
9. Verify optimistic locking prevents conflicts

### Expected Results:
- Each user has independent session
- Changes visible to all users after refresh
- No data corruption
- Proper conflict resolution

---

## Test Scenario 18: Performance Testing

**Objective:** Verify system performs well under load

### Manual Performance Checks:

1. **Kanban Load Time**
   - Measure time to load Kanban with 6 orders
   - Target: < 2 seconds

2. **Analysis Wizard Load**
   - Measure time to load full analysis
   - Target: < 3 seconds

3. **Import Processing**
   - Import 100 orders via XLSX
   - Measure processing time
   - Target: < 30 seconds

4. **Search/Filter**
   - Filter Kanban
   - Target: < 500ms

### Expected Results:
- Acceptable load times for typical data volumes
- No memory leaks
- Database queries optimized (use EXPLAIN)
- Lazy loading for related entities

---

## Browser Compatibility

Test on the following browsers:

- [ ] Chrome (latest)
- [ ] Firefox (latest)
- [ ] Safari (latest)
- [ ] Edge (latest)

### Key Features to Verify:
- Drag-and-drop (HTMX)
- File upload
- Date pickers
- Bootstrap components
- AJAX requests

---

## Accessibility Testing

Basic accessibility checks:

1. **Keyboard Navigation**
   - Tab through all forms
   - Verify focus indicators visible
   - Test Enter key submits forms

2. **Screen Reader**
   - Test with VoiceOver (Mac) or NVDA (Windows)
   - Verify labels read correctly
   - Check ARIA attributes

3. **Color Contrast**
   - Verify text readable
   - Test with color blindness simulator

---

## Security Testing

Basic security checks:

1. **SQL Injection**
   - Try entering SQL in search fields
   - Verify parameterized queries prevent injection

2. **XSS (Cross-Site Scripting)**
   - Enter `<script>alert('XSS')</script>` in text fields
   - Verify output escaped

3. **File Upload Security**
   - Try uploading malicious files
   - Verify file type validation

4. **Authorization**
   - Try accessing admin pages without proper role
   - Verify access denied

---

## Smoke Test Checklist

Quick sanity check for deployments:

- [ ] Application starts without errors
- [ ] Home page loads
- [ ] Can select profile
- [ ] Kanban displays orders
- [ ] Can open analysis
- [ ] Can complete analysis
- [ ] Configuration page loads
- [ ] Import page loads
- [ ] No JavaScript errors in console
- [ ] No 500 errors in logs

---

## Known Issues / Future Enhancements

Document any known issues or planned improvements:

1. Real-time notifications (WebSocket)
2. Email integration for workflow events
3. PDF export for parecer CRM
4. Advanced search/filtering on Kanban
5. Batch operations (approve multiple orders)
6. Dashboard with KPIs and charts
7. Integration with external score providers
8. Mobile app

---

## Test Data Cleanup

To reset test data:

1. Stop application
2. Delete H2 database file (if persisted)
3. Restart application
4. DataInitializer will reload test data

Or use H2 console:
```sql
-- Delete all data
DELETE FROM DOCUMENTO;
DELETE FROM DUPLICATA;
DELETE FROM DADOS_BI;
DELETE FROM CHEQUE;
DELETE FROM ACAO_JUDICIAL;
DELETE FROM PROTESTO;
DELETE FROM PEFIN;
DELETE FROM PARTICIPACAO;
DELETE FROM SOCIO;
DELETE FROM ANALISE;
DELETE FROM PEDIDO;
DELETE FROM CLIENTE;
DELETE FROM GRUPO_ECONOMICO;
DELETE FROM CONFIGURACAO;
```

Then restart application to reload test data.

---

## Reporting Issues

When filing bug reports, include:

1. Steps to reproduce
2. Expected behavior
3. Actual behavior
4. Browser/OS version
5. Screenshots/videos
6. Console errors
7. Network tab (for API calls)

---

## Conclusion

This manual testing guide complements the automated integration tests and ensures comprehensive coverage of the Credit Analysis System. Regular execution of these scenarios helps maintain quality and catch regressions early.

For automated E2E testing with browser automation (Selenium/Playwright), see future enhancement plans.
