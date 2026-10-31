package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevocacionIntegrationTest {

    private static final String NUMERO = "+5491123456789";
    private static final String PATH = "/api/v1/credenciales/{numero}/revocacion";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CredencialRepository repository;
    @MockitoSpyBean
    private CredencialAuditLogRepository auditRepository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("Revoca inmediatamente por política del banco y audita sin token")
    void revocaPorBanco() throws Exception {
        var sesion = crear(CredencialEstado.ACTIVA);

        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"POLITICA_BANCO\",\"solicitadoPor\":\"BANCO\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(repository.findByJti(sesion.getJti())).hasValueSatisfying(revocada -> {
            assertThat(revocada.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
            assertThat(revocada.getFechaRevocacion()).isNotNull();
            assertThat(revocada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.POLITICA_BANCO);
            assertThat(revocada.getRevocadaPor()).isEqualTo(RevocadaPor.BANCO);
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.REVOCACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(evento.getJti()).isEqualTo(sesion.getJti());
            assertThat(evento.getMotivo()).isEqualTo("POLITICA_BANCO");
        });
    }

    @Test
    @DisplayName("Acepta el contrato actual de H3 y rechaza una segunda revocación con auditoría")
    void aceptaH3YRechazaRepeticion() throws Exception {
        var sesion = crear(CredencialEstado.ACTIVA);
        String solicitud = "{\"motivo\":\"SOLICITUD_CLIENTE\"}";
        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(sesion.getId())).hasValueSatisfying(revocada -> {
            assertThat(revocada.getRevocadaPor()).isEqualTo(RevocadaPor.CANAL);
            assertThat(revocada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.BLOQUEO);
        });

        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
        assertThat(auditRepository.findAll()).hasSize(2).anySatisfy(evento -> {
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
            assertThat(evento.getMotivo()).isEqualTo("BLOQUEO");
        });
    }

    @Test
    @DisplayName("Un fallo de auditoría revierte la revocación y conserva el rechazo independiente")
    void revierteSiFallaAuditoria() throws Exception {
        var sesion = crear(CredencialEstado.ACTIVA);
        doThrow(new DataIntegrityViolationException("Fallo inyectado"))
                .doAnswer(invocacion -> auditRepository.saveAndFlush(invocacion.getArgument(0)))
                .when(auditRepository).save(any());

        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"FRAUDE\",\"solicitadoPor\":\"SISTEMA\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(repository.findById(sesion.getId())).hasValueSatisfying(activa -> {
            assertThat(activa.getEstado()).isEqualTo(CredencialEstado.ACTIVA);
            assertThat(activa.getFechaRevocacion()).isNull();
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
            assertThat(evento.getMotivo()).isEqualTo("FRAUDE");
        });
    }

    @Test
    @DisplayName("Una credencial expirada no se revoca y genera auditoría de rechazo")
    void rechazaExpirada() throws Exception {
        var sesion = crear(CredencialEstado.EXPIRADA);
        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"DESVINCULACION\",\"solicitadoPor\":\"CANAL\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(repository.findById(sesion.getId())).hasValueSatisfying(expirada -> {
            assertThat(expirada.getEstado()).isEqualTo(CredencialEstado.EXPIRADA);
            assertThat(expirada.getFechaRevocacion()).isNull();
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.REVOCACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
            assertThat(evento.getMotivo()).isEqualTo("DESVINCULACION");
        });
    }

    @Test
    @DisplayName("Valida motivo y número y no modifica credenciales ante solicitudes inválidas")
    void rechazaSolicitudInvalida() throws Exception {
        crear(CredencialEstado.ACTIVA);
        mockMvc.perform(post(PATH, NUMERO)
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(PATH, "invalido")
                .header("Authorization", "Bearer h3-test-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"FRAUDE\",\"solicitadoPor\":\"SISTEMA\"}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isPresent();
    }

    private CredencialSesion crear(CredencialEstado estado) {
        return repository.saveAndFlush(CredencialSesion.builder().jti(UUID.randomUUID().toString())
                .numeroWhatsapp(NUMERO).documentoTipo("DNI").documentoNumero("12345678")
                .vinculoId(UUID.randomUUID()).estado(estado).fechaEmision(Instant.now())
                .fechaExpiracion(Instant.now().plusSeconds(3600)).build());
    }
}
