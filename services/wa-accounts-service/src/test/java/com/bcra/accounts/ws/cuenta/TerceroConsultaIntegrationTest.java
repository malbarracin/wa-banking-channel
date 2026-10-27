package com.bcra.accounts.ws.cuenta;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.TerceroAgendadoRepository;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TerceroConsultaIntegrationTest {

    private static final String CREDENCIAL = "sesion-validada";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private TerceroAgendadoRepository terceroAgendadoRepository;

    @Autowired
    private AuditoriaCuentaRepository auditoriaCuentaRepository;

    @MockitoBean
    private CredencialesServiceClient credencialesServiceClient;

    @BeforeEach
    void prepararAgenda() {
        auditoriaCuentaRepository.deleteAll();
        terceroAgendadoRepository.deleteAll();
        guardarTercero("propio", "DNI", "123", true);
        guardarTercero("inactivo", "DNI", "123", false);
        guardarTercero("ajeno", "DNI", "456", true);
        guardarTercero("otro-tipo", "PASAPORTE", "123", true);
        when(credencialesServiceClient.validar(CREDENCIAL)).thenReturn(titular("123"));
        clearInvocations(terceroAgendadoRepository);
    }

    @Test
    @DisplayName("Lista solo terceros activos del documento validado y audita la consulta")
    void listaSoloActivosDelTitular() throws Exception {
        mockMvc.perform(get("/api/v1/terceros").header("X-Credencial-Sesion", CREDENCIAL)
                        .param("documentoNumero", "456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].alias").value("propio"))
                .andExpect(jsonPath("$[0].numeroCuentaDestino").value("cuenta-propio"))
                .andExpect(jsonPath("$[0].bancoDestino").value("Banco destino"))
                .andExpect(jsonPath("$[0].titularDocumentoNumero").doesNotExist())
                .andExpect(jsonPath("$[0].titularDocumentoTipo").doesNotExist())
                .andExpect(jsonPath("$[0].activo").doesNotExist());

        assertThat(auditoriaCuentaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.LISTAR_TERCEROS);
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
            assertThat(auditoria.getDocumentoTipo()).isEqualTo("DNI");
            assertThat(auditoria.getDocumentoNumero()).isEqualTo("123");
            assertThat(auditoria.getNumeroWhatsapp()).isEqualTo("+5491112345678");
            assertThat(auditoria.getCuentaId()).isNull();
            assertThat(auditoria.getFecha()).isNotNull();
        });
    }

    @Test
    @DisplayName("Un titular sin destinatarios recibe una lista vacía auditada")
    void devuelveAgendaVacia() throws Exception {
        when(credencialesServiceClient.validar(CREDENCIAL)).thenReturn(titular("789"));

        mockMvc.perform(get("/api/v1/terceros").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(auditoriaCuentaRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalida", "expirada", "revocada"})
    @DisplayName("Rechaza credenciales no válidas antes de consultar la agenda")
    void rechazaCredencialSinConsultarAgenda(String credencial) throws Exception {
        when(credencialesServiceClient.validar(credencial))
                .thenThrow(new UnauthorizedException("Credencial no válida"));
        var solicitud = get("/api/v1/terceros");
        if (!credencial.isEmpty()) {
            solicitud.header("X-Credencial-Sesion", credencial);
        }

        mockMvc.perform(solicitud)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(terceroAgendadoRepository);
        assertThat(auditoriaCuentaRepository.count()).isZero();
    }

    @Test
    @DisplayName("Revalida la credencial en cada consulta y aplica una revocación inmediata")
    void revalidaEnCadaConsulta() throws Exception {
        when(credencialesServiceClient.validar(CREDENCIAL)).thenReturn(titular("123"))
                .thenThrow(new UnauthorizedException("Credencial no válida"));

        mockMvc.perform(get("/api/v1/terceros").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isOk());
        clearInvocations(terceroAgendadoRepository);
        mockMvc.perform(get("/api/v1/terceros").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(credencialesServiceClient, times(2)).validar(CREDENCIAL);
        verifyNoInteractions(terceroAgendadoRepository);
        assertThat(auditoriaCuentaRepository.count()).isEqualTo(1);
    }

    private ValidarCredencialResponse titular(String documentoNumero) {
        return new ValidarCredencialResponse(true, "+5491112345678", "DNI", documentoNumero,
                Instant.now().plusSeconds(600));
    }

    private void guardarTercero(String alias, String documentoTipo, String documentoNumero, boolean activo) {
        TerceroAgendado tercero = new TerceroAgendado();
        tercero.setAlias(alias);
        tercero.setTitularDocumentoTipo(documentoTipo);
        tercero.setTitularDocumentoNumero(documentoNumero);
        tercero.setNumeroCuentaDestino("cuenta-" + alias);
        tercero.setBancoDestino("Banco destino");
        tercero.setActivo(activo);
        terceroAgendadoRepository.saveAndFlush(tercero);
    }
}
