package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse;
import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "credencial.vigencia-minutos=7")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmisionAtomicaIntegrationTest {

    private static final String NUMERO = "+5491123456789";
    private static final long VIGENCIA_SEGUNDOS = 7 * 60;
    private static final String REQUEST = """
            {"numeroWhatsapp":"+5491123456789","documentoTipo":"DNI","documentoNumero":"12345678",
             "vinculoActivo":true,"identidadVerificada":true}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CredencialAuditLogRepository auditRepository;
    @MockitoSpyBean
    private CredencialRepository repository;
    @MockitoSpyBean
    private JwtEncoder jwtEncoder;
    @MockitoBean
    private UsersServiceClient usersServiceClient;

    @BeforeEach
    void prepararCliente() {
        repository.deleteAll();
        auditRepository.deleteAll();
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.ACTIVE));
    }

    @Test
    @DisplayName("Una falla al insertar revierte la revocación y su auditoría, pero conserva el rechazo")
    void revierteReemplazoSiFallaInsercion() throws Exception {
        String jtiAnterior = emitirYObtenerJti();
        doThrow(new DataIntegrityViolationException("detalle SQL protegido"))
                .when(repository).saveAndFlush(any(CredencialSesion.class));

        rechazar(409, "CONFLICT");

        verificarCredencialYAuditoriaAnteriores(jtiAnterior);
    }

    @Test
    @DisplayName("Una falla de firma conserva la credencial anterior y audita el rechazo sin exponer detalles")
    void conservaAnteriorSiFallaFirma() throws Exception {
        String jtiAnterior = emitirYObtenerJti();
        doThrow(new JwtEncodingException("detalle de clave protegido"))
                .when(jwtEncoder).encode(any(JwtEncoderParameters.class));

        rechazar(500, "INTERNAL_ERROR");

        verificarCredencialYAuditoriaAnteriores(jtiAnterior);
    }

    @Test
    @DisplayName("La vigencia configurada se refleja en la persistencia y en la respuesta")
    void aplicaVigenciaConfigurada() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/credenciales/emitir")
                .header("Authorization", "Bearer h3-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Instant expiracion = Instant.parse(objectMapper.readTree(respuesta).get("fechaExpiracion").asText());

        assertThat(repository.findAll()).singleElement().satisfies(credencial -> {
            assertThat(credencial.getFechaExpiracion()).isEqualTo(expiracion);
            assertThat(expiracion).isEqualTo(credencial.getFechaEmision().plusSeconds(VIGENCIA_SEGUNDOS));
        });
    }

    private String emitirYObtenerJti() throws Exception {
        mockMvc.perform(post("/api/v1/credenciales/emitir").header("Authorization", "Bearer h3-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        return repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)
                .orElseThrow().getJti();
    }

    private void rechazar(int estado, String codigo) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/credenciales/emitir")
                .header("Authorization", "Bearer h3-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().is(estado))
                .andExpect(jsonPath("$.code").value(codigo))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.credencial").doesNotExist())
                .andExpect(jsonPath("$.stacktrace").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("detalle SQL protegido", "detalle de clave protegido", NUMERO);
    }

    private void verificarCredencialYAuditoriaAnteriores(String jtiAnterior) {
        assertThat(repository.findAll()).singleElement().satisfies(credencial -> {
            assertThat(credencial.getJti()).isEqualTo(jtiAnterior);
            assertThat(credencial.getEstado()).isEqualTo(CredencialEstado.ACTIVA);
            assertThat(credencial.getFechaRevocacion()).isNull();
            assertThat(credencial.getMotivoRevocacion()).isNull();
        });
        assertThat(auditRepository.findAll()).hasSize(2).allSatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.EMISION);
            if (evento.getResultado() == AuditResultado.EXITOSO) {
                assertThat(evento.getJti()).isEqualTo(jtiAnterior);
            } else {
                assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
                assertThat(evento.getJti()).isNull();
            }
        });
        assertThat(auditRepository.findAll()).extracting(evento -> evento.getResultado())
                .containsExactlyInAnyOrder(AuditResultado.EXITOSO, AuditResultado.RECHAZADO);
    }
}
