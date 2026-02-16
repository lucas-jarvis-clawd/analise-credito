# Thymeleaf Fragments Usage Guide

## Overview

This project uses reusable Thymeleaf fragments to improve code organization and maintainability. All fragments are located in `src/main/resources/templates/fragments/`.

## Available Fragments

### 1. Layout Fragments (`fragments/layout.html`)

#### Head Fragment
Includes meta tags, title, Bootstrap CSS, and Bootstrap Icons.

```html
<head th:replace="~{fragments/layout :: head('Page Title')}"></head>
```

**Usage Example:**
```html
<head th:replace="~{fragments/layout :: head('Kanban Dashboard')}"></head>
```

#### Navbar Fragment
Standard navigation bar with menu items.

```html
<nav th:replace="~{fragments/layout :: navbar}"></nav>
```

#### Footer Fragment
Standard footer with copyright information.

```html
<footer th:replace="~{fragments/layout :: footer}"></footer>
```

#### Scripts Fragment
Bootstrap JavaScript bundle.

```html
<div th:replace="~{fragments/layout :: scripts}"></div>
```

---

### 2. Score Badge Fragment (`fragments/score-badge.html`)

Displays a score with conditional color styling:
- Green (bg-success): score >= 700
- Yellow (bg-warning): score >= 400
- Red (bg-danger): score < 400

```html
<span th:replace="~{fragments/score-badge :: badge(${cliente.scoreBoaVista})}"></span>
```

**Example in context:**
```html
<div class="card-body">
    <p>Score do Cliente:</p>
    <span th:replace="~{fragments/score-badge :: badge(${analise.pedido.cliente.scoreBoaVista})}"></span>
</div>
```

---

### 3. Alert Badges Fragment (`fragments/alert-badges.html`)

Displays a list of alert badges.

```html
<div th:replace="~{fragments/alert-badges :: badges(${pedido.alerts})}"></div>
```

**Example in context:**
```html
<div class="card-alerts" th:if="${pedido.alerts != null and !pedido.alerts.isEmpty()}">
    <div th:replace="~{fragments/alert-badges :: badges(${pedido.alerts})}"></div>
</div>
```

---

### 4. Kanban Card Fragment (`fragments/card-pedido.html`)

Renders kanban cards for a list of análises. This fragment handles:
- Empty state (no análises)
- Card display with cliente info, pedido details, alerts, score, and action button

```html
<div th:replace="~{fragments/card-pedido :: kanban-cards(${analises})}"></div>
```

**Example in context:**
```html
<div class="kanban-column" data-status="PENDENTE">
    <div class="kanban-column-header">
        <span>Pendente</span>
        <span class="kanban-column-count" th:text="${analises.size()}">0</span>
    </div>
    <div th:replace="~{fragments/card-pedido :: kanban-cards(${analises})}"></div>
</div>
```

---

## Complete Template Example

Here's how to use fragments in a complete template:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('My Page Title')}"></head>
<body>
    <!-- Navigation -->
    <nav th:replace="~{fragments/layout :: navbar}"></nav>

    <!-- Main Content -->
    <div class="container mt-4">
        <h1>My Page</h1>

        <!-- Display client score -->
        <div class="mb-3">
            <span>Score: </span>
            <span th:replace="~{fragments/score-badge :: badge(${cliente.scoreBoaVista})}"></span>
        </div>

        <!-- Display alerts -->
        <div class="mb-3" th:if="${alerts != null and !alerts.isEmpty()}">
            <span>Alertas: </span>
            <div th:replace="~{fragments/alert-badges :: badges(${alerts})}"></div>
        </div>
    </div>

    <!-- Footer -->
    <footer th:replace="~{fragments/layout :: footer}"></footer>

    <!-- Scripts -->
    <div th:replace="~{fragments/layout :: scripts}"></div>

    <!-- Optional: Page-specific scripts -->
    <script>
        // Your custom JavaScript here
    </script>
</body>
</html>
```

---

## Benefits

1. **Code Reusability**: Write once, use everywhere
2. **Maintainability**: Update fragments in one place
3. **Consistency**: Ensure consistent UI across all pages
4. **Cleaner Code**: Reduce template duplication
5. **Easier Testing**: Test fragments in isolation

---

## Fragments Already Used

- `kanban.html`: Uses `fragments/card-pedido :: kanban-cards`

## Templates Ready to Use Fragments

All existing templates can be updated to use the layout fragments:
- `index.html` - Can use head, scripts fragments
- `analise.html` - Can use head, navbar, footer, scripts, score-badge, alert-badges fragments
- `configuracao.html` - Can use head, navbar, footer, scripts fragments
- `importacao.html` - Can use head, navbar, footer, scripts fragments
- `importacao-resultado.html` - Can use head, navbar, footer, scripts fragments

---

## Next Steps

1. Update existing templates to use fragments (optional)
2. Create additional fragments as needed (e.g., breadcrumbs, pagination)
3. Add custom styling to fragments if necessary

---

Generated: 2026-02-15
