package AnaliseCredito.Analise_de_Credito.presentation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da importação de arquivos XLSX.
 *
 * Contém contadores de registros importados, avisos e erros.
 * Usa estratégia leniente: continua processamento mesmo com erros,
 * permitindo importações parciais.
 */
@Data
public class ResultadoImportacao {

    // Status geral
    private String status; // SUCESSO, SUCESSO_PARCIAL, ERRO

    // Contadores
    private int clientesImportados = 0;
    private int pedidosImportados = 0;
    private int dadosBIImportados = 0;
    private int duplicatasImportadas = 0;

    // Mensagens
    private List<String> erros = new ArrayList<>();
    private List<String> avisos = new ArrayList<>();

    // Métodos auxiliares

    public void incrementarClientes() {
        this.clientesImportados++;
    }

    public void incrementarPedidos() {
        this.pedidosImportados++;
    }

    public void incrementarDadosBI() {
        this.dadosBIImportados++;
    }

    public void incrementarDuplicatas() {
        this.duplicatasImportadas++;
    }

    public void addErro(String erro) {
        this.erros.add(erro);
    }

    public void addAviso(String aviso) {
        this.avisos.add(aviso);
    }

    public boolean temErros() {
        return !erros.isEmpty();
    }

    public boolean temAvisos() {
        return !avisos.isEmpty();
    }

    public int getTotalImportado() {
        return clientesImportados + pedidosImportados + dadosBIImportados + duplicatasImportadas;
    }

    /**
     * Define status automaticamente baseado em erros
     */
    public void finalizarComStatus() {
        if (!temErros() && getTotalImportado() > 0) {
            this.status = "SUCESSO";
        } else if (temErros() && getTotalImportado() > 0) {
            this.status = "SUCESSO_PARCIAL";
        } else {
            this.status = "ERRO";
        }
    }
}
