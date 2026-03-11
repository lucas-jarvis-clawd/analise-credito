package AnaliseCredito.Analise_de_Credito.application.service;

import AnaliseCredito.Analise_de_Credito.domain.model.GrupoEconomico;
import AnaliseCredito.Analise_de_Credito.domain.model.HistoricoLimite;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.GrupoEconomicoRepository;
import AnaliseCredito.Analise_de_Credito.infrastructure.persistence.HistoricoLimiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimiteServiceTest {

    @Mock
    private GrupoEconomicoRepository grupoRepository;

    @Mock
    private HistoricoLimiteRepository historicoRepository;

    @InjectMocks
    private LimiteService limiteService;

    private GrupoEconomico grupo;

    @BeforeEach
    void setUp() {
        grupo = new GrupoEconomico();
        grupo.setId(1L);
        grupo.setCodigo("001");
        grupo.setNome("Grupo Teste");
        grupo.setLimiteAprovado(BigDecimal.ZERO);
    }

    @Test
    void atualizarLimite_devePersistirHistoricoEAtualizarGrupo() {
        when(grupoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        limiteService.atualizarLimite(1L, new BigDecimal("75000.00"), "FINANCEIRO");

        // Verifica que HistoricoLimite foi salvo
        ArgumentCaptor<HistoricoLimite> captor = ArgumentCaptor.forClass(HistoricoLimite.class);
        verify(historicoRepository).save(captor.capture());
        HistoricoLimite salvo = captor.getValue();
        assertEquals(new BigDecimal("75000.00"), salvo.getValor());
        assertEquals("FINANCEIRO", salvo.getResponsavel());
        assertEquals(grupo, salvo.getGrupoEconomico());

        // Verifica que o grupo foi atualizado
        assertEquals(new BigDecimal("75000.00"), grupo.getLimiteAprovado());
        verify(grupoRepository).save(grupo);
    }

    @Test
    void atualizarLimite_deveLancarExcecaoParaGrupoInexistente() {
        when(grupoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> limiteService.atualizarLimite(99L, new BigDecimal("1000.00"), "FINANCEIRO"));

        verifyNoInteractions(historicoRepository);
    }

    @Test
    void atualizarLimite_devePermitirZerarLimite() {
        grupo.setLimiteAprovado(new BigDecimal("50000.00"));
        when(grupoRepository.findById(1L)).thenReturn(Optional.of(grupo));

        limiteService.atualizarLimite(1L, BigDecimal.ZERO, "FINANCEIRO");

        assertEquals(BigDecimal.ZERO, grupo.getLimiteAprovado());
        verify(historicoRepository).save(any(HistoricoLimite.class));
    }
}
