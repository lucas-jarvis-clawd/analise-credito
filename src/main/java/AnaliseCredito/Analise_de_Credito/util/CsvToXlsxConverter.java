package AnaliseCredito.Analise_de_Credito.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utilitário para converter arquivos CSV em XLSX.
 *
 * Usado para gerar dados de exemplo para testes da aplicação.
 */
public class CsvToXlsxConverter {

    public static void main(String[] args) {
        String baseDir = "dados-exemplo/";

        String[] files = {"Clientes", "Pedidos", "DadosBI", "Duplicatas"};

        for (String fileName : files) {
            try {
                convertCsvToXlsx(baseDir + fileName + ".csv", baseDir + fileName + ".xlsx");
                System.out.println("✅ Convertido: " + fileName + ".xlsx");
            } catch (Exception e) {
                System.err.println("❌ Erro ao converter " + fileName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n🎉 Conversão completa! Arquivos XLSX gerados em " + baseDir);
    }

    /**
     * Converte um arquivo CSV para XLSX.
     */
    public static void convertCsvToXlsx(String csvFilePath, String xlsxFilePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Dados");

        // Estilo para o cabeçalho
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Ler CSV e escrever no XLSX
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            int rowNum = 0;

            while ((line = br.readLine()) != null) {
                Row row = sheet.createRow(rowNum);
                String[] values = line.split(",");

                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);

                    // Aplicar estilo no cabeçalho
                    if (rowNum == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }

                rowNum++;
            }
        }

        // Auto-ajustar largura das colunas
        for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
            sheet.autoSizeColumn(i);
        }

        // Salvar XLSX
        try (FileOutputStream fileOut = new FileOutputStream(xlsxFilePath)) {
            workbook.write(fileOut);
        }

        workbook.close();
    }
}
