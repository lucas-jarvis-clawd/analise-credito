# Guia de Design - Sistema de Análise de Crédito Texcotton

## 🎨 Visão Geral

O frontend da aplicação foi completamente redesenhado seguindo a identidade visual da **Texcotton**, criando uma experiência elegante, profissional e moderna que reflete os valores da marca.

---

## 🎯 Paleta de Cores

### Cores Principais
- **Preto**: `#000000` - Cor dominante, profissional e sofisticada
- **Dourado**: `#D4AF37` - Accent color premium, elegância e confiança
- **Dourado Claro**: `#F4D03F` - Variação para gradientes
- **Dourado Escuro**: `#B8860B` - Contraste e profundidade
- **Branco**: `#FFFFFF` - Clareza e espaçamento

### Cores Semânticas
- **Sucesso**: `#2ECC71` (verde vibrante)
- **Aviso**: `#F39C12` (laranja/amarelo)
- **Perigo**: `#E74C3C` (vermelho)
- **Info**: `#3498DB` (azul)

---

## ✍️ Tipografia

### Fontes
- **Display/Títulos**: `Cormorant Garamond` (serifada elegante)
  - Usada para: logos, títulos principais, cabeçalhos de seção
  - Peso: 600-700
  - Características: Clássica, sofisticada, alta legibilidade

- **Corpo/Interface**: `Inter` (sans-serif moderna)
  - Usada para: textos, formulários, cards, badges
  - Peso: 400-600
  - Características: Limpa, profissional, ótima em telas

### Hierarquia
```
h1: 3rem (48px) - Logo/Brand
h2: 2.25rem (36px) - Títulos de página
h3: 1.75rem (28px) - Seções de workflow
h4: 1.5rem (24px) - Cards headers
body: 15px - Texto base
```

---

## 🧩 Componentes Principais

### 1. Navbar (navbar-texcotton)
**Design:**
- Fundo: Gradiente preto (#000 → #1a1a1a)
- Borda inferior: 2px dourada
- Logo: Fonte display com ícone animado
- Links: Hover com sublinhado dourado animado
- User badge: Cápsula preta com borda dourada

**Animações:**
- Pulse no ícone do logo (2s)
- Underline animado nos links
- Hover state com background dourado translúcido

### 2. Cards (card-texcotton)
**Estrutura:**
```html
<div class="card-texcotton">
  <div class="card-texcotton-header">
    <!-- Header preto com gradiente + borda dourada -->
  </div>
  <div class="card-texcotton-body">
    <!-- Conteúdo branco -->
  </div>
</div>
```

**Características:**
- Header: Gradiente preto com efeito radial dourado
- Borda inferior: 3px dourada
- Shadow: Elevação suave
- Hover: Lift effect (translateY -4px)

### 3. Kanban Board
**Inovações de UX:**

#### Colunas (kanban-column)
- Background: Branco limpo
- Border: 2px cinza claro
- Header: Tipografia display, separador dourado
- Counter badge: Gradiente dourado, animação pop
- Drag-over state: Background dourado translúcido + borda tracejada

#### Cards (kanban-card)
- Background: Gradiente sutil (branco → #fafafa)
- Border left: 4px dourada (accent visual)
- Hover: Lift + scale (1.02) + shadow upgrade
- Dragging: Rotate 3° + opacity 0.6 + shadow XL
- Grab cursor: Visual feedback

**Animações:**
1. **Load animation**: Cards aparecem staggered (delay 50ms cada)
2. **Drag feedback**: Smooth rotation + opacity change
3. **Drop success**: Scale down + fade out antes do reload
4. **Hover**: Translatey -4px + border expansion

#### Micro-interações
- Smooth scroll horizontal (mouse drag)
- Custom scrollbar: Dourado sobre cinza
- Status badges com gradiente e shadow
- Botão "Analisar": Shimmer effect no hover

### 4. Badges

#### Score Badges
```css
.score-alto   → Verde (#2ECC71-#27AE60) gradient
.score-medio  → Dourado (#F4D03F-#D4AF37) gradient
.score-baixo  → Vermelho (#E74C3C-#C0392B) gradient
```
- Border-radius: Full (pill shape)
- Font-weight: 700 (bold)
- Box-shadow: Colored glow matching badge

#### Alert Badges
- Appearance animation: Scale + translateY
- Uppercase lettering
- Icon + text combination

### 5. Botões

#### Primary (btn-texcotton-primary)
- Background: Gradiente dourado (135deg)
- Shimmer effect: Linha branca atravessando
- Hover: Lift + shadow dourada
- Font-weight: 600

#### Secondary (btn-texcotton-secondary)
- Background: Preto
- Border: 2px dourada
- Text: Dourado
- Hover: Inverte (background dourado, text preto)

#### Outline (btn-texcotton-outline)
- Transparent background
- Border: 2px preta
- Hover: Background preto + text dourado

---

## 📱 Páginas Redesenhadas

### 1. Login/Home (index.html)
**Design Conceitual:**
- Background: Gradiente preto diagonal com pattern animado
- Card central: Glass morphism effect
- Logo: Grande, centralizado, com animação pulse
- Profile buttons: Cards interativos com shimmer
- Admin links: Discretos, hover dourado

**Efeitos Especiais:**
- Animated dot pattern (drift 30s)
- Top gold shimmer line (3s pulse)
- Card appear: Fade-in-up 0.8s
- Rotating radial gradient no header
- Hover states premium

### 2. Dashboard Kanban (kanban.html)
**Layout:**
- Header: Título display + filtros pill-style
- Two workflows: BASE_PRAZO e CLIENTE_NOVO
- Cada workflow: Título com ícone + múltiplas colunas
- Scroll horizontal suave

**UX Enhancements:**
- Staggered card loading (50ms delay)
- Drag-and-drop visual feedback
- Status update com animação
- Mouse wheel + drag scroll
- Empty state iconográfico

### 3. Importação (importacao.html)
**Melhorias:**
- Navbar integrada
- Card principal: Header preto + body branco
- Instruções em alert estilizado
- 4 file inputs numerados com cores
- Botões: Texcotton theme

### 4. Configuração (configuracao.html)
**Organização:**
- 4 cards separados por categoria:
  1. Limites SIMEI
  2. Thresholds de Score
  3. Multiplicadores
  4. Critérios de Alçada
- Forms: Inputs limpos, labels uppercase
- Success alerts: Verde com ícone

---

## 🎬 Animações e Transições

### Timing Functions
```css
--transition-fast: 150ms cubic-bezier(0.4, 0, 0.2, 1)
--transition-base: 250ms cubic-bezier(0.4, 0, 0.2, 1)
--transition-slow: 350ms cubic-bezier(0.4, 0, 0.2, 1)
--transition-spring: 500ms cubic-bezier(0.34, 1.56, 0.64, 1)
```

### Keyframe Animations
1. **pulse-gold**: Logo icon (2s infinite)
2. **fade-in-up**: Page entrance (0.6s)
3. **slide-in-right**: Elements (0.4s)
4. **count-pop**: Badge counters (spring)
5. **badge-appear**: Alert badges (scale + translateY)
6. **drift**: Background pattern (30s)
7. **shimmer**: Gold line effect (3s)
8. **rotate-slow**: Radial gradient (20s)

---

## 🔧 Sistema de Design Tokens

### Spacing Scale
```css
--space-xs: 0.25rem    (4px)
--space-sm: 0.5rem     (8px)
--space-md: 1rem       (16px)
--space-lg: 1.5rem     (24px)
--space-xl: 2rem       (32px)
--space-2xl: 3rem      (48px)
--space-3xl: 4rem      (64px)
```

### Border Radius
```css
--radius-sm: 4px
--radius-md: 8px
--radius-lg: 12px
--radius-xl: 16px
--radius-full: 9999px
```

### Shadows
```css
--shadow-sm: 0 1px 3px rgba(0,0,0,0.08)
--shadow-md: 0 4px 12px rgba(0,0,0,0.10)
--shadow-lg: 0 8px 24px rgba(0,0,0,0.12)
--shadow-xl: 0 16px 48px rgba(0,0,0,0.15)
--shadow-gold: 0 4px 20px rgba(212,175,55,0.25)
```

---

## 📊 Princípios de UX/UI

### 1. Hierarquia Visual
- **Cores**: Preto domina, dourado acentua elementos importantes
- **Tipografia**: Títulos display, corpo sans-serif
- **Espaçamento**: Generoso, respirável
- **Contraste**: Alto para legibilidade

### 2. Feedback Visual
- **Hover states**: Sempre presente em elementos interativos
- **Loading states**: Animações durante carregamento
- **Success/Error**: Cores semânticas + ícones
- **Drag feedback**: Opacity + rotation + shadow

### 3. Consistência
- **Bordas douradas**: Marca visual consistente
- **Gradientes**: Sempre 135deg diagonal
- **Shadows**: Escalas predefinidas
- **Transitions**: Mesmas timing functions

### 4. Acessibilidade
- **Contraste**: WCAG AA+ compliant
- **Font sizes**: Mínimo 15px
- **Touch targets**: Mínimo 44px
- **Focus states**: Visíveis

### 5. Performance
- **CSS-only animations**: Sem JavaScript desnecessário
- **will-change**: Elementos animados
- **GPU acceleration**: Transform + opacity
- **Lazy loading**: Cards aparecem progressivamente

---

## 🚀 Características Premium

### Glass Morphism
- Login card: `backdrop-filter: blur(10px)`
- Navbar: `backdrop-filter: blur(10px)`

### Gradientes Sofisticados
- 135deg diagonal (elegante)
- Multi-stop para profundidade
- Radial para backgrounds

### Shimmer Effects
- Botões primary: Linha branca atravessando
- Cards: Hover com gradiente translúcido

### Micro-interactions
- Badge pop animation
- Count numbers
- Icon rotations
- Underline animations

---

## 📝 Notas de Implementação

### Arquivos Modificados
1. ✅ `/static/css/texcotton-theme.css` - CSS principal (850+ linhas)
2. ✅ `/templates/fragments/layout.html` - Layout base + navbar
3. ✅ `/templates/index.html` - Tela de login
4. ✅ `/templates/kanban.html` - Dashboard principal
5. ✅ `/templates/importacao.html` - Upload de dados
6. ✅ `/templates/configuracao.html` - Settings
7. ✅ `/templates/fragments/card-pedido.html` - Kanban cards
8. ✅ `/templates/fragments/score-badge.html` - Score badges
9. ✅ `/templates/fragments/alert-badges.html` - Alert badges

### Compatibilidade
- ✅ Chrome/Edge (Chromium 90+)
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Mobile responsive (breakpoint 768px)

### Performance
- CSS minificado: ~45KB
- Google Fonts: 2 famílias (preloaded)
- Bootstrap 5.3.2: Já presente
- Bootstrap Icons: CDN

---

## 🎓 Como Usar

### Classes Principais
```html
<!-- Navbar -->
<nav class="navbar-texcotton">...</nav>

<!-- Cards -->
<div class="card-texcotton">
  <div class="card-texcotton-header">...</div>
  <div class="card-texcotton-body">...</div>
</div>

<!-- Botões -->
<button class="btn btn-texcotton-primary">Primary</button>
<button class="btn btn-texcotton-secondary">Secondary</button>
<button class="btn btn-texcotton-outline">Outline</button>

<!-- Badges -->
<span class="badge-texcotton badge-score score-alto">Score: 850</span>
<span class="badge-texcotton badge-danger">Alerta!</span>

<!-- Kanban -->
<div class="kanban-container">
  <div class="kanban-column">
    <div class="kanban-column-header">
      <span>Título</span>
      <span class="kanban-column-count">5</span>
    </div>
    <div class="kanban-card">...</div>
  </div>
</div>
```

### CSS Variables
```css
/* Cores */
var(--tx-black)
var(--tx-gold)
var(--tx-white)

/* Fontes */
var(--font-display)
var(--font-body)

/* Espaçamento */
var(--space-md)

/* Sombras */
var(--shadow-gold)

/* Transições */
var(--transition-base)
```

---

## 🌟 Diferenciais do Design

### 1. Identidade Única
- Não é um template genérico
- Cores Texcotton em todos os elementos
- Tipografia display elegante
- Animações premium

### 2. Profissionalismo
- Palette corporativa (preto + dourado)
- Hierarquia visual clara
- Espaçamento generoso
- Detalhes polidos

### 3. UX Moderna
- Drag-and-drop fluido
- Feedback visual imediato
- Micro-interações delightful
- Loading states animados

### 4. Técnico Excellence
- CSS modular e escalável
- Design tokens
- BEM-like naming
- Performance otimizada

---

## 📌 Próximos Passos (Opcional)

### Melhorias Futuras
1. ⬜ Dark mode toggle
2. ⬜ Skeleton loaders
3. ⬜ Toast notifications system
4. ⬜ Advanced filters (multi-select)
5. ⬜ Charts/dashboards (Chart.js + gold theme)
6. ⬜ PDF export com branding
7. ⬜ Wizard steps animation
8. ⬜ Mobile app (PWA)

### Otimizações
1. ⬜ CSS purge (remover unused Bootstrap)
2. ⬜ Lazy load fonts
3. ⬜ Image optimization
4. ⬜ Service worker (offline)

---

## 📞 Suporte

Para dúvidas sobre o design system:
- Consulte este guia
- Veja `/static/css/texcotton-theme.css` (bem comentado)
- Exemplos em cada template HTML

---

**Design criado em:** 15/02/2026
**Versão:** 1.0.0
**Tema:** Texcotton Premium
**Framework:** Bootstrap 5.3.2 + Custom CSS

---

## 🎨 Screenshots Conceituais

### Login Page
```
┌─────────────────────────────────────────────┐
│  ════════════  (gold line shimmer)         │
│                                             │
│         ╔═══════════════════════╗          │
│         ║  ████████████████████ ║          │
│         ║   💰 (pulse icon)     ║          │
│         ║    TEXCOTTON         ║          │
│         ║  Credit Analysis      ║          │
│         ╠═══════════════════════╣ gold     │
│         ║                       ║          │
│         ║  Selecione perfil:   ║          │
│         ║                       ║          │
│         ║  [🧮 FINANCEIRO    ] ║ gradient │
│         ║                       ║ hover    │
│         ║  [👤 COMERCIAL     ] ║ shimmer  │
│         ║                       ║          │
│         ║  ─────────────────    ║          │
│         ║  Admin: [📤] [⚙️]   ║          │
│         ╚═══════════════════════╝          │
│                                             │
│    Texcotton © 2026 | v1.0.0              │
└─────────────────────────────────────────────┘
```

### Kanban Dashboard
```
┌──────────────────────────────────────────────────────────────────────┐
│ ████ TEXCOTTON  [Dashboard][Import][Config]     👤 FINANCEIRO      │
│ ════ (gold underline)                                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Dashboard Kanban                  [Todos][Base/Prazo][Cliente Novo]│
│                                                                      │
│  ══════ Workflow: BASE/PRAZO                                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐│
│  │Pendente │  │Em Análise│  │Aprovado │  │Reprovado│  │Finalizado││
│  │    [3] │  │    [5]  │  │    [12] │  │    [2]  │  │    [45]  ││
│  ├─────────┤  ├─────────┤  ├─────────┤  ├─────────┤  ├─────────┤│
│  │┌───────┐│  │┌───────┐│  │┌───────┐│  │┌───────┐│  │┌───────┐││
│  ││ Card  ││  ││ Card  ││  ││ Card  ││  ││ Card  ││  ││ Card  │││
│  ││ ▌     ││  ││ ▌     ││  ││ ▌     ││  ││ ▌     ││  ││ ▌     │││
│  │└───────┘│  │└───────┘│  │└───────┘│  │└───────┘│  │└───────┘││
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘│
│                 ▌= gold accent border                              │
│                                                                      │
│  ══════ Workflow: CLIENTE NOVO                                     │
│  [Similar layout...]                                                │
└──────────────────────────────────────────────────────────────────────┘
```

---

**Made with ❤️ and ☕ for Texcotton**
