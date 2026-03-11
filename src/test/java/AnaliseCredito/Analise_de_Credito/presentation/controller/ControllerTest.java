package AnaliseCredito.Analise_de_Credito.presentation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests using MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Spring context loads with all controllers
    }

    @Test
    void relatorio_deveRetornarPaginaComGrupos() throws Exception {
        mockMvc.perform(get("/relatorio/limites")
                .sessionAttr("perfil", "FINANCEIRO"))
            .andExpect(status().isOk())
            .andExpect(view().name("relatorio-limites"))
            .andExpect(model().attributeExists("grupos"))
            .andExpect(model().attributeExists("perfil"));
    }

    @Test
    void relatorio_historico_deveRetornarFragment() throws Exception {
        mockMvc.perform(get("/relatorio/limites/1/historico")
                .sessionAttr("perfil", "FINANCEIRO"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/historico-limite-modal :: corpo"));
    }
}
