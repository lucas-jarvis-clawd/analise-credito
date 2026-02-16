# Dados de Exemplo - Sistema de Análise de Crédito

## 📁 Arquivos Gerados

Este diretório contém dados de exemplo para testar a aplicação:

- **Clientes.xlsx** - 10 clientes de exemplo com diferentes perfis
- **Pedidos.xlsx** - 15 pedidos distribuídos entre os clientes
- **DadosBI.xlsx** - Dados de Business Intelligence (2 coleções por grupo)
- **Duplicatas.xlsx** - 25 duplicatas com diferentes situações

## 🎯 Perfil dos Dados

### Clientes (10 empresas)

| CNPJ | Razão Social | Tipo | Score | SIMEI | Situação | Grupo |
|------|--------------|------|-------|-------|----------|-------|
| 12345678000195 | BOUTIQUE FASHION LTDA | BASE_PRAZO | 750 | Não | Ativa/Normal | GE001 |
| 23456789000186 | MAGAZINE STYLE ME | BASE_PRAZO | 750 | Não | Ativa/Normal | GE001 |
| 34567890000177 | LOJA POPULAR LTDA | BASE_PRAZO | 320 | **Sim** | Ativa/Normal | GE002 |
| 45678901000168 | FASHION KIDS LTDA | **CLIENTE_NOVO** | 450 | **Sim** | Pendente | GE003 |
| 56789012000159 | MEGA STORE SA | BASE_PRAZO | 680 | Não | Ativa/Atraso | GE004 |
| 67890123000140 | MODAS ELITE LTDA | **CLIENTE_NOVO** | 520 | Não | Pendente | GE005 |
| 78901234000131 | TOP VAREJO ME | BASE_PRAZO | 380 | **Sim** | Ativa/Normal | GE006 |
| 89012345000122 | ROUPAS & CIA LTDA | ANTECIPADO | 620 | Não | Ativa/Normal | GE007 |
| 90123456000113 | BOUTIQUE TRENDS ME | BASE_PRAZO | 290 | **Sim** | Ativa/Normal | GE008 |
| 01234567000104 | FASHION GROUP SA | BASE_PRAZO | 820 | Não | Ativa/Normal | GE009 |

**Destaques:**
- **GE001:** Grupo com 2 empresas (Boutique Fashion + Magazine Style)
- **3 clientes SIMEI:** Loja Popular, Fashion Kids, Top Varejo, Boutique Trends
- **2 clientes novos:** Fashion Kids (bloqueio 80), Modas Elite (bloqueio 36)
- **Scores variados:** De 290 (baixo) até 820 (alto)

### Pedidos (15 pedidos)

| Pedido | Cliente | Valor | Bloqueio | Workflow | Colecão |
|--------|---------|-------|----------|----------|---------|
| PED004 | Fashion Kids | R$ 52.000 | **80** | CLIENTE_NOVO | 202601 |
| PED006 | Modas Elite | R$ 42.000 | **36** | CLIENTE_NOVO | 202602 |
| PED001 | Boutique Fashion | R$ 45.000 | 10 | BASE_PRAZO | 202601 |
| PED005 | Mega Store | R$ 67.000 | 20 | BASE_PRAZO | 202601 |
| PED010 | Fashion Group | R$ 73.000 | 20 | BASE_PRAZO | 202602 |
| ... | ... | ... | ... | ... | ... |

**Destaques:**
- **2 pedidos workflow CLIENTE_NOVO** (PED004, PED006)
- **13 pedidos workflow BASE_PRAZO**
- Valores de R$ 15.000 a R$ 73.000
- Distribuídos em 2 coleções (202601 e 202602)

### Dados BI (18 registros)

- **2 coleções por grupo:** 202512 (dezembro/2025) e 202601 (janeiro/2026)
- **Scores internos:** De 280 (baixo) até 870 (excelente)
- **Crédito:** De R$ 0 (clientes novos) até R$ 168.000
- **Atrasos:** De 0 até 20 dias (média)

### Duplicatas (25 títulos)

**Posições:**
- **CARTEIRA:** 18 títulos (em dia ou a vencer)
- **COBRANCA:** 4 títulos (vencidos em cobrança)
- **NEGATIVACAO:** 3 títulos (negativados)

**Situações de Atraso:**
- **Pagas em dia:** 4 duplicatas
- **Pagas com atraso:** 3 duplicatas (de 2 a 27 dias)
- **Vencidas não pagas:** 7 duplicatas (de 15 a 183 dias)
- **A vencer:** 11 duplicatas

## 🚀 Como Usar

### 1. Acessar a tela de importação

```
http://localhost:8081/importacao
```

### 2. Fazer upload dos 4 arquivos

- Selecione `Clientes.xlsx`
- Selecione `Pedidos.xlsx`
- Selecione `DadosBI.xlsx`
- Selecione `Duplicatas.xlsx`

### 3. Processar a importação

Clique em **"Processar Importação"** e aguarde o resultado.

### 4. Explorar o sistema

Após a importação, acesse:
- **Dashboard Kanban:** `/analise/kanban`
- **Filtro "PRAZO":** Ver apenas pedidos BASE_PRAZO (13 pedidos)
- **Filtro "NOVO":** Ver apenas pedidos CLIENTE_NOVO (2 pedidos)
- **Filtro "TODOS":** Ver tudo lado a lado

## 📊 Cenários de Teste Cobertos

### ✅ Alertas que serão gerados:

1. **SIMEI > LIMITE**
   - PED004: Fashion Kids (SIMEI, R$ 52.000 > limite de R$ 35.000)

2. **PEDIDO > LIMITE**
   - PED005: Mega Store (R$ 67.000 pode exceder limite calculado)
   - PED010: Fashion Group (R$ 73.000 pode exceder limite calculado)

3. **SCORE BAIXO**
   - PED009: Boutique Trends (score 290 < 300)
   - PED003/PED015: Loja Popular (score 320 < 300 threshold)

4. **RESTRIÇÕES**
   - Clientes com duplicatas em NEGATIVACAO (3 empresas)

### ✅ Cálculos que serão testados:

- **Limite Sugerido:** Baseado no maior crédito das últimas 2 coleções × multiplicador do score
- **Atraso Atual:** Calculado das duplicatas vencidas e não pagas
- **Maior Atraso Última Coleção:** Maior atraso entre todas as duplicatas
- **Total Restrições:** Soma de PEFIN + Protestos + Ações + Cheques

### ✅ Workflows que serão criados:

- **13 análises BASE_PRAZO:** Status inicial PENDENTE
- **2 análises CLIENTE_NOVO:** Status inicial PENDENTE

## 🎯 O que Esperar Após Importação

### Dashboard Kanban - Seção BASE_PRAZO

```
┌─────────────┬──────────────┬─────────────┐
│  Pendente   │ Em Análise   │   Parecer   │
│     13      │      0       │      0      │
└─────────────┴──────────────┴─────────────┘
```

### Dashboard Kanban - Seção CLIENTE_NOVO

```
┌─────────────┬──────────────┬─────────────┐
│  Pendente   │ Doc Solic.   │ Doc Enviada │
│      2      │      0       │      0      │
└─────────────┴──────────────┴─────────────┘
```

### Cards Esperados com Badges

**PED004 - Fashion Kids:**
- 🔴 SIMEI > LIMITE
- 🟡 SCORE BAIXO (450)

**PED009 - Boutique Trends:**
- 🔴 SCORE BAIXO (290)
- 🟡 RESTRIÇÕES (3)

**PED005 - Mega Store:**
- ⚠️ PEDIDO > LIMITE (possível)
- 🟡 RESTRIÇÕES

## 📝 Notas

- Todos os dados são **fictícios** e gerados apenas para teste
- CNPJs são **inválidos** (não passam verificação de dígito)
- Os valores e situações foram criados para demonstrar **todos os cenários** da aplicação
- Os scores Boa Vista variam para testar **multiplicadores diferentes** no cálculo de limite

## 🔧 Regenerar os Dados

Se precisar regenerar os arquivos XLSX:

```bash
mvn exec:java -Dexec.mainClass="AnaliseCredito.Analise_de_Credito.util.CsvToXlsxConverter"
```

Os arquivos CSV originais estão preservados para referência.
