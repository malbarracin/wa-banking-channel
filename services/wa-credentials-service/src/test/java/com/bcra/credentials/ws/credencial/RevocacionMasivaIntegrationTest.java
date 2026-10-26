package com.bcra.credentials.ws.credencial;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevocacionMasivaIntegrationTest {

    private static final String ENDPOINT = "/api/v1/credenciales/revocacion-masiva";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CredencialRepository repository;
    @MockitoSpyBean
    private CredencialAuditLogRepository auditRepository;

    @BeforeEach
    void prepararCredenciales() {
        auditRepository.deleteAll();
        repository.deleteAll();
        guardar("+5491111111111", "12345678", CredencialEstado.ACTIVA);
        guardar("+5491122222222", "12345678", CredencialEstado.ACTIVA);
        guardar("+5491133333333", "87654321", CredencialEstado.ACTIVA);
        guardar("+5491144444444", "12345678", CredencialEstado.EXPIRADA);
        guardar("+5491155555555", "12345678", CredencialEstado.REVOCADA);
    }

    @Test
    @DisplayName("TODAS revoca solamente las activas y repetir no duplica auditoría")
    void revocaTodasConAuditoriaIdempotente() throws Exception {
        revocar("{\"criterio\":\"TODAS\"}", 3);
        revocar("{\"criterio\":\"TODAS\"}", 0);

        assertThat(repository.findAll()).filteredOn(c -> c.getFechaRevocacion() != null)
                .hasSize(3).allSatisfy(c -> {
                    assertThat(c.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
                    assertThat(c.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REVOCACION_MASIVA);
                    assertThat(c.getRevocadaPor()).isEqualTo(RevocadaPor.BANCO);
                });
        assertThat(repository.findAll()).filteredOn(c -> c.getEstado() == CredencialEstado.EXPIRADA).hasSize(1);
        assertThat(auditRepository.findAll()).hasSize(3).allSatisfy(a -> {
            assertThat(a.getAccion()).isEqualTo(AuditAccion.REVOCACION);
            assertThat(a.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(a.getMotivo()).isEqualTo("REVOCACION_MASIVA");
            assertThat(a.getJti()).isNotBlank();
            assertThat(a.getFecha()).isNotNull();
        });
    }

    @Test
    @DisplayName("POR_CLIENTE alcanza todos sus números y respeta a otros clientes y tipos documentales")
    void revocaSoloClienteSeleccionado() throws Exception {
        revocar("""
                {"criterio":"POR_CLIENTE","segmento":{"documentoTipo":"PASAPORTE","documentoNumero":"12345678"}}
                """, 0);
        String request = """
                {"criterio":"POR_CLIENTE","segmento":{"documentoTipo":"DNI","documentoNumero":"12345678"}}
                """;
        revocar(request, 2);
        revocar(request, 0);

        assertThat(repository.findByNumeroWhatsappAndEstado("+5491133333333", CredencialEstado.ACTIVA)).isPresent();
        assertThat(auditRepository.findAll()).hasSize(2)
                .allSatisfy(a -> assertThat(a.getDocumentoNumero()).isEqualTo("12345678"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"criterio\":\"OTRO\"}", "{\"criterio\":\"POR_CLIENTE\"}",
        "{\"criterio\":\"POR_CLIENTE\",\"segmento\":{}}",
        "{\"criterio\":\"TODAS\",\"segmento\":{\"documentoTipo\":\"DNI\",\"documentoNumero\":\"12345678\"}}"
    })
    @DisplayName("Rechaza alcances inválidos con contrato 400 sin modificar credenciales")
    void validaAlcanceSinAmpliarlo(String request) throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(repository.findAll()).filteredOn(c -> c.getEstado() == CredencialEstado.ACTIVA).hasSize(3);
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    @DisplayName("Si falla una auditoría se revierten todas las revocaciones")
    void revierteSiFallaAuditoria() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("fallo de prueba"))
                .when(auditRepository).save(any(CredencialAuditLog.class));

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"criterio\":\"TODAS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(repository.findAll()).filteredOn(c -> c.getEstado() == CredencialEstado.ACTIVA).hasSize(3);
        assertThat(auditRepository.count()).isZero();
    }

    private void revocar(String request, int cantidad) throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revocadas").value(cantidad));
    }

    private void guardar(String numero, String documento, CredencialEstado estado) {
        repository.saveAndFlush(CredencialSesion.builder().jti(UUID.randomUUID().toString())
                .numeroWhatsapp(numero).documentoTipo("DNI").documentoNumero(documento)
                .vinculoId(UUID.randomUUID()).estado(estado).fechaEmision(Instant.now())
                .fechaExpiracion(Instant.now().plusSeconds(3600)).build());
    }
}
