package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.enums.PosicaoDuplicata;
import AnaliseCredito.Analise_de_Credito.domain.enums.StatusWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoCliente;
import AnaliseCredito.Analise_de_Credito.domain.enums.TipoWorkflow;
import AnaliseCredito.Analise_de_Credito.domain.model.*;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.*;
import AnaliseCredito.Analise_de_Credito.presentation.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ImportacaoService - Serviço de importação de dados via arquivos XLSX.
 *
 * Este é o serviço central de importação que:
 * 1. Recebe 4 arquivos XLSX (Clientes, Pedidos, DadosBI, Duplicatas)
 * 2. Parseia com Apache POI
 * 3. Valida dados (modo leniente - continua em erros)
 * 4. Importa em ordem: Clientes → Pedidos → DadosBI → Duplicatas
 * 5. Cria GrupoEconomico automaticamente se não existe
 * 6. Define workflow pelo bloqueio ao criar Pedido
 * 7. Cria Analise PENDENTE para cada pedido
 * 8. Pós-processamento: calcula limites sugeridos e alertas
 *
 * ESTRATÉGIA DE TRANSAÇÃO: Parcial
 * - Cada método importarX() tem sua própria transação
 * - Registros válidos são salvos mesmo se outros falharem
 * - Erros são logados no ResultadoImportacao
 *
 * DUPLICATAS: Estratégia skip
 * - CNPJs duplicados são ignorados com aviso
 * - Permite re-importação segura (idempotente)
 */
@Service
public class ImportacaoService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private GrupoEconomicoRepository grupoEconomicoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private AnaliseRepository analiseRepository;

    @Autowired
    private DadosBIRepository dadosBIRepository;

    @Autowired
    private DuplicataRepository duplicataRepository;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private AlertaService alertaService;

    /**
     * Processa importação completa dos 4 arquivos XLSX.
     *
     * @param clientes Arquivo Clientes.xlsx
     * @param pedidos Arquivo Pedidos.xlsx
     * @param dadosBI Arquivo DadosBI.xlsx
     * @param duplicatas Arquivo Duplicatas.xlsx
     * @return Resultado da importação com contadores e erros
     */
    public ResultadoImportacao processar(MultipartFile clientes,
                                         MultipartFile pedidos,
                                         MultipartFile dadosBI,
                                         MultipartFile duplicatas) {
        ResultadoImportacao resultado = new ResultadoImportacao();

        try {
            // 1. Parse files with Apache POI
            List<ClienteDTO> clientesData = parseClientes(clientes);
            List<PedidoDTO> pedidosData = parsePedidos(pedidos);
            List<DadosBIDTO> dadosBIData = parseDadosBI(dadosBI);
            List<DuplicataDTO> duplicatasData = parseDuplicatas(duplicatas);

            // 2. Import in order (each method is @Transactional)
            importarClientes(clientesData, resultado);
            importarPedidos(pedidosData, resultado);
            importarDadosBI(dadosBIData, resultado);
            importarDuplicatas(duplicatasData, resultado);

            // 3. Post-processing
            calcularLimitesSugeridos(resultado);
            calcularAlertas(resultado);

            // 4. Set final status
            resultado.finalizarComStatus();

        } catch (Exception e) {
            resultado.setStatus("ERRO");
            resultado.addErro("Erro geral na importação: " + e.getMessage());
        }

        return resultado;
    }

    // ========== PARSING METHODS (Apache POI) ==========

    /**
     * Parseia arquivo Clientes.xlsx
     * Colunas: cnpj, razao_social, nome_fantasia, telefone, email, estado,
     *          tipo, data_fundacao, simei, situacao_credito, situacao_cobranca,
     *          cluster, grupo_economico, score_boa_vista, score_boa_vista_data, sintegra
     */
    private List<ClienteDTO> parseClientes(MultipartFile file) throws IOException {
        List<ClienteDTO> clientes = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) rows.next();

            int rowNum = 1;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;

                try {
                    ClienteDTO dto = new ClienteDTO();
                    dto.setCnpj(getStringValue(row, 0));
                    dto.setRazaoSocial(getStringValue(row, 1));
                    dto.setNomeFantasia(getStringValue(row, 2));
                    dto.setTelefone(getStringValue(row, 3));
                    dto.setEmail(getStringValue(row, 4));
                    dto.setEstado(getStringValue(row, 5));
                    dto.setTipo(getStringValue(row, 6));
                    dto.setDataFundacao(getDateValue(row, 7));
                    dto.setSimei(getBooleanValue(row, 8));
                    dto.setSituacaoCredito(getStringValue(row, 9));
                    dto.setSituacaoCobranca(getStringValue(row, 10));
                    dto.setCluster(getStringValue(row, 11));
                    dto.setGrupoEconomico(getStringValue(row, 12));
                    dto.setScoreBoaVista(getIntegerValue(row, 13));
                    dto.setScoreBoaVistaData(getDateValue(row, 14));
                    dto.setSintegra(getStringValue(row, 15));

                    clientes.add(dto);
                } catch (Exception e) {
                    // Log row error but continue
                    System.err.println("Erro ao parsear linha " + rowNum + " de Clientes: " + e.getMessage());
                }
            }
        }

        return clientes;
    }

    /**
     * Parseia arquivo Pedidos.xlsx
     * Colunas: numero, data, valor, cnpj_cliente, marca, bloqueio,
     *          deposito, condicao_pagamento, colecao
     */
    private List<PedidoDTO> parsePedidos(MultipartFile file) throws IOException {
        List<PedidoDTO> pedidos = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) rows.next();

            int rowNum = 1;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;

                try {
                    PedidoDTO dto = new PedidoDTO();
                    dto.setNumero(getStringValue(row, 0));
                    dto.setData(getDateValue(row, 1));
                    dto.setValor(getBigDecimalValue(row, 2));
                    dto.setCnpjCliente(getStringValue(row, 3));
                    dto.setMarca(getStringValue(row, 4));
                    dto.setBloqueio(getStringValue(row, 5));
                    dto.setDeposito(getStringValue(row, 6));
                    dto.setCondicaoPagamento(getStringValue(row, 7));
                    dto.setColecao(getIntegerValue(row, 8));

                    pedidos.add(dto);
                } catch (Exception e) {
                    System.err.println("Erro ao parsear linha " + rowNum + " de Pedidos: " + e.getMessage());
                }
            }
        }

        return pedidos;
    }

    /**
     * Parseia arquivo DadosBI.xlsx
     * Colunas: grupo_economico, colecao, valor_vencido, credito, score, atraso_medio
     */
    private List<DadosBIDTO> parseDadosBI(MultipartFile file) throws IOException {
        List<DadosBIDTO> dadosBI = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) rows.next();

            int rowNum = 1;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;

                try {
                    DadosBIDTO dto = new DadosBIDTO();
                    dto.setGrupoEconomico(getStringValue(row, 0));
                    dto.setColecao(getIntegerValue(row, 1));
                    dto.setValorVencido(getBigDecimalValue(row, 2));
                    dto.setCredito(getBigDecimalValue(row, 3));
                    dto.setScore(getIntegerValue(row, 4));
                    dto.setAtrasoMedio(getBigDecimalValue(row, 5));

                    dadosBI.add(dto);
                } catch (Exception e) {
                    System.err.println("Erro ao parsear linha " + rowNum + " de DadosBI: " + e.getMessage());
                }
            }
        }

        return dadosBI;
    }

    /**
     * Parseia arquivo Duplicatas.xlsx
     * Colunas: cnpj, posicao, portador, vencimento, valor, saldo, data_pagamento
     */
    private List<DuplicataDTO> parseDuplicatas(MultipartFile file) throws IOException {
        List<DuplicataDTO> duplicatas = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) rows.next();

            int rowNum = 1;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;

                try {
                    DuplicataDTO dto = new DuplicataDTO();
                    dto.setCnpj(getStringValue(row, 0));
                    dto.setPosicao(getStringValue(row, 1));
                    dto.setPortador(getStringValue(row, 2));
                    dto.setVencimento(getDateValue(row, 3));
                    dto.setValor(getBigDecimalValue(row, 4));
                    dto.setSaldo(getBigDecimalValue(row, 5));
                    dto.setDataPagamento(getDateValue(row, 6));

                    duplicatas.add(dto);
                } catch (Exception e) {
                    System.err.println("Erro ao parsear linha " + rowNum + " de Duplicatas: " + e.getMessage());
                }
            }
        }

        return duplicatas;
    }

    // ========== CELL VALUE EXTRACTORS ==========

    private String getStringValue(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK -> null;
            default -> null;
        };
    }

    private Integer getIntegerValue(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? null : Integer.parseInt(value);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) return BigDecimal.ZERO;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue())
                        .setScale(2, RoundingMode.HALF_UP);
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return value.isEmpty() ? BigDecimal.ZERO :
                       new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private LocalDate getDateValue(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Boolean getBooleanValue(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) return false;

        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim().toUpperCase();
                yield "TRUE".equals(value) || "SIM".equals(value) || "1".equals(value);
            }
            case NUMERIC -> cell.getNumericCellValue() > 0;
            default -> false;
        };
    }

    // ========== IMPORT METHODS ==========

    /**
     * Importa clientes com estratégia de skip para duplicados.
     * Cria GrupoEconomico automaticamente se não existir.
     */
    @Transactional
    public void importarClientes(List<ClienteDTO> data, ResultadoImportacao resultado) {
        for (ClienteDTO dto : data) {
            try {
                // Validar CNPJ obrigatório
                if (dto.getCnpj() == null || dto.getCnpj().trim().isEmpty()) {
                    resultado.addErro("Cliente sem CNPJ - linha ignorada");
                    continue;
                }

                // Skip se já existe (idempotente)
                if (clienteRepository.findByCnpj(dto.getCnpj()).isPresent()) {
                    resultado.addAviso("Cliente " + dto.getCnpj() + " já existe - ignorado");
                    continue;
                }

                // 1. Find or create GrupoEconomico
                String codigoGrupo = (dto.getGrupoEconomico() != null && !dto.getGrupoEconomico().trim().isEmpty())
                        ? dto.getGrupoEconomico() : dto.getCnpj();
                GrupoEconomico grupo = findOrCreateGrupo(codigoGrupo);

                // 2. Create Cliente
                Cliente cliente = new Cliente();
                cliente.setCnpj(dto.getCnpj());
                cliente.setRazaoSocial(dto.getRazaoSocial() != null ? dto.getRazaoSocial() : "Razão Social");
                cliente.setNomeFantasia(dto.getNomeFantasia());
                cliente.setTelefone(dto.getTelefone());
                cliente.setEmail(dto.getEmail());
                cliente.setEstado(dto.getEstado());
                cliente.setDataFundacao(dto.getDataFundacao());
                cliente.setSimei(dto.getSimei() != null ? dto.getSimei() : false);
                cliente.setSituacaoCredito(dto.getSituacaoCredito());
                cliente.setSituacaoCobranca(dto.getSituacaoCobranca());
                cliente.setCluster(dto.getCluster());
                cliente.setScoreBoaVista(dto.getScoreBoaVista());
                cliente.setScoreBoaVistaData(dto.getScoreBoaVistaData());
                cliente.setSintegra(dto.getSintegra());
                cliente.setGrupoEconomico(grupo);

                // Parse TipoCliente
                TipoCliente tipoCliente = parseTipoCliente(dto.getTipo());
                cliente.setTipoCliente(tipoCliente);

                clienteRepository.save(cliente);
                resultado.incrementarClientes();

            } catch (Exception e) {
                resultado.addErro("Cliente " + dto.getCnpj() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Importa pedidos e cria análises PENDENTE.
     * Define workflow baseado no código de bloqueio.
     */
    @Transactional
    public void importarPedidos(List<PedidoDTO> data, ResultadoImportacao resultado) {
        for (PedidoDTO dto : data) {
            try {
                // Validar campos obrigatórios
                if (dto.getCnpjCliente() == null || dto.getNumero() == null) {
                    resultado.addErro("Pedido sem CNPJ ou número - linha ignorada");
                    continue;
                }

                // Buscar cliente
                Cliente cliente = clienteRepository.findByCnpj(dto.getCnpjCliente())
                        .orElseThrow(() -> new RuntimeException("Cliente não encontrado: " + dto.getCnpjCliente()));

                // Criar pedido
                Pedido pedido = new Pedido();
                pedido.setNumero(dto.getNumero());
                pedido.setData(dto.getData() != null ? dto.getData() : LocalDate.now());
                pedido.setValor(dto.getValor() != null ? dto.getValor() : BigDecimal.ZERO);
                pedido.setMarca(dto.getMarca());
                pedido.setDeposito(dto.getDeposito());
                pedido.setCondicaoPagamento(dto.getCondicaoPagamento());
                pedido.setColecao(dto.getColecao());
                pedido.setBloqueio(dto.getBloqueio());
                pedido.setCliente(cliente);

                // Define workflow by bloqueio
                if ("80".equals(dto.getBloqueio()) || "36".equals(dto.getBloqueio())) {
                    pedido.setWorkflow(TipoWorkflow.CLIENTE_NOVO);
                } else {
                    pedido.setWorkflow(TipoWorkflow.BASE_PRAZO);
                }

                pedidoRepository.save(pedido);

                // Create Analise PENDENTE
                Analise analise = new Analise();
                analise.setPedido(pedido);
                analise.setClienteId(cliente.getId());
                analise.setGrupoEconomicoId(cliente.getGrupoEconomico().getId());
                analise.setStatusWorkflow(StatusWorkflow.PENDENTE);
                analise.setDataInicio(LocalDateTime.now());
                analiseRepository.save(analise);

                resultado.incrementarPedidos();

            } catch (Exception e) {
                resultado.addErro("Pedido " + dto.getNumero() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Importa dados de BI vinculados ao grupo econômico.
     */
    @Transactional
    public void importarDadosBI(List<DadosBIDTO> data, ResultadoImportacao resultado) {
        for (DadosBIDTO dto : data) {
            try {
                // Validar campos obrigatórios
                if (dto.getGrupoEconomico() == null || dto.getColecao() == null) {
                    resultado.addErro("DadosBI sem grupo ou coleção - linha ignorada");
                    continue;
                }

                // Buscar grupo econômico
                GrupoEconomico grupo = grupoEconomicoRepository.findByCodigo(dto.getGrupoEconomico())
                        .orElseThrow(() -> new RuntimeException("Grupo não encontrado: " + dto.getGrupoEconomico()));

                // Criar DadosBI
                DadosBI dadosBI = new DadosBI();
                dadosBI.setGrupoEconomico(grupo);
                dadosBI.setColecao(dto.getColecao());
                dadosBI.setValorVencido(dto.getValorVencido() != null ? dto.getValorVencido() : BigDecimal.ZERO);
                dadosBI.setCredito(dto.getCredito() != null ? dto.getCredito() : BigDecimal.ZERO);
                dadosBI.setScore(dto.getScore());
                dadosBI.setAtrasoMedio(dto.getAtrasoMedio() != null ? dto.getAtrasoMedio() : BigDecimal.ZERO);
                dadosBI.setDataImportacao(LocalDateTime.now());

                dadosBIRepository.save(dadosBI);
                resultado.incrementarDadosBI();

            } catch (Exception e) {
                resultado.addErro("DadosBI " + dto.getGrupoEconomico() + "/" + dto.getColecao() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Importa duplicatas vinculadas ao cliente.
     */
    @Transactional
    public void importarDuplicatas(List<DuplicataDTO> data, ResultadoImportacao resultado) {
        for (DuplicataDTO dto : data) {
            try {
                // Validar campos obrigatórios
                if (dto.getCnpj() == null || dto.getVencimento() == null) {
                    resultado.addErro("Duplicata sem CNPJ ou vencimento - linha ignorada");
                    continue;
                }

                // Buscar cliente
                Cliente cliente = clienteRepository.findByCnpj(dto.getCnpj())
                        .orElseThrow(() -> new RuntimeException("Cliente não encontrado: " + dto.getCnpj()));

                // Criar duplicata
                Duplicata duplicata = new Duplicata();
                duplicata.setCliente(cliente);
                duplicata.setVencimento(dto.getVencimento());
                duplicata.setValor(dto.getValor() != null ? dto.getValor() : BigDecimal.ZERO);
                duplicata.setSaldo(dto.getSaldo() != null ? dto.getSaldo() : BigDecimal.ZERO);
                duplicata.setPortador(dto.getPortador());
                duplicata.setDataPagamento(dto.getDataPagamento());

                // Parse posição
                PosicaoDuplicata posicao = parsePosicaoDuplicata(dto.getPosicao());
                duplicata.setPosicao(posicao);

                duplicataRepository.save(duplicata);
                resultado.incrementarDuplicatas();

            } catch (Exception e) {
                resultado.addErro("Duplicata " + dto.getCnpj() + ": " + e.getMessage());
            }
        }
    }

    // ========== POST-PROCESSING ==========

    /**
     * Calcula limites sugeridos para todas as análises usando ScoringService.
     * Leniente: não falha importação se cálculo de um limite falhar.
     */
    @Transactional
    public void calcularLimitesSugeridos(ResultadoImportacao resultado) {
        try {
            List<Analise> analises = analiseRepository.findAll();
            int sucessos = 0;

            for (Analise analise : analises) {
                try {
                    GrupoEconomico grupo = grupoEconomicoRepository.findById(analise.getGrupoEconomicoId())
                            .orElseThrow(() -> new RuntimeException("Grupo não encontrado"));

                    BigDecimal limite = scoringService.calcularLimiteSugerido(grupo);
                    analise.setLimiteSugerido(limite);
                    analiseRepository.save(analise);
                    sucessos++;

                } catch (Exception e) {
                    // Log but don't fail entire import
                    resultado.addAviso("Erro ao calcular limite para análise " + analise.getId() + ": " + e.getMessage());
                }
            }

            if (sucessos > 0) {
                resultado.addAviso("Limites sugeridos calculados: " + sucessos);
            }

        } catch (Exception e) {
            resultado.addAviso("Erro no cálculo de limites: " + e.getMessage());
        }
    }

    /**
     * Calcula alertas para todos os pedidos usando AlertaService.
     * Alertas são armazenados transientemente (não persistidos).
     */
    public void calcularAlertas(ResultadoImportacao resultado) {
        try {
            List<Pedido> pedidos = pedidoRepository.findAll();
            int sucessos = 0;

            for (Pedido pedido : pedidos) {
                try {
                    List<String> alertas = alertaService.calcularAlertas(pedido);
                    pedido.setAlerts(alertas);
                    sucessos++;

                } catch (Exception e) {
                    // Log but don't fail
                    resultado.addAviso("Erro ao calcular alertas para pedido " + pedido.getId() + ": " + e.getMessage());
                }
            }

            if (sucessos > 0) {
                resultado.addAviso("Alertas calculados: " + sucessos);
            }

        } catch (Exception e) {
            resultado.addAviso("Erro no cálculo de alertas: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Busca ou cria GrupoEconomico pelo código.
     * Se não existir, cria um novo com limites zerados.
     */
    private GrupoEconomico findOrCreateGrupo(String codigo) {
        return grupoEconomicoRepository.findByCodigo(codigo)
                .orElseGet(() -> {
                    GrupoEconomico grupo = new GrupoEconomico();
                    grupo.setCodigo(codigo);
                    grupo.setNome("Grupo " + codigo);
                    grupo.setLimiteAprovado(BigDecimal.ZERO);
                    grupo.setLimiteDisponivel(BigDecimal.ZERO);
                    return grupoEconomicoRepository.save(grupo);
                });
    }

    /**
     * Parseia string para enum TipoCliente.
     * Default: BASE_PRAZO
     */
    private TipoCliente parseTipoCliente(String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return TipoCliente.BASE_PRAZO;
        }

        try {
            return TipoCliente.valueOf(tipo.trim().toUpperCase());
        } catch (Exception e) {
            return TipoCliente.BASE_PRAZO;
        }
    }

    /**
     * Parseia string para enum PosicaoDuplicata.
     * Default: CARTEIRA
     */
    private PosicaoDuplicata parsePosicaoDuplicata(String posicao) {
        if (posicao == null || posicao.trim().isEmpty()) {
            return PosicaoDuplicata.CARTEIRA;
        }

        try {
            return PosicaoDuplicata.valueOf(posicao.trim().toUpperCase());
        } catch (Exception e) {
            return PosicaoDuplicata.CARTEIRA;
        }
    }
}
