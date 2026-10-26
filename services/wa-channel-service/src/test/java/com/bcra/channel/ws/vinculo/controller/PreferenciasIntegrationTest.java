package com.bcra.channel.ws.vinculo.controller;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.vinculo.CredencialHeaders;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreferenciasIntegrationTest {

    private static final String NUMERO = "+5491199990031";
    private static final String RUTA = "/api/v1/vinculos/{numero}/preferencias";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final VinculoRepository vinculoRepository;

    @Autowired
    PreferenciasIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper,
            VinculoRepository vinculoRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.vinculoRepository = vinculoRepository;
    }

    @BeforeEach
    void limpiar() {
        vinculoRepository.deleteAll();
    }

    @Test
    @DisplayName("PATCH preferencias con cambio válido confirma idioma y notificaciones actualizados")
    void actualizaPreferenciasConCredencialYVinculoActivo() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, "en", false, "08:00-12:00");

        mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"es\",\"notificacionesHabilitadas\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.preferencias.idioma").value("es"))
                .andExpect(jsonPath("$.preferencias.notificacionesHabilitadas").value(true))
                .andExpect(jsonPath("$.preferencias.horarioPreferido").value("08:00-12:00"));

        Vinculo guardado = vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO)
                .orElseThrow();
        assertThat(guardado.getPreferencias().getIdioma()).isEqualTo("es");
        assertThat(guardado.getPreferencias().isNotificacionesHabilitadas()).isTrue();
        assertThat(guardado.getPreferencias().getHorarioPreferido()).isEqualTo("08:00-12:00");
    }

    @Test
    @DisplayName("PATCH preferencias con solo horarioPreferido lo actualiza vía MapStruct")
    void actualizaSoloHorarioPreferido() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, "es", true, "08:00-12:00");

        mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horarioPreferido\":\"20:00-22:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferencias.idioma").value("es"))
                .andExpect(jsonPath("$.preferencias.notificacionesHabilitadas").value(true))
                .andExpect(jsonPath("$.preferencias.horarioPreferido").value("20:00-22:00"));

        Vinculo guardado = vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO)
                .orElseThrow();
        assertThat(guardado.getPreferencias().getHorarioPreferido()).isEqualTo("20:00-22:00");
    }

    @Test
    @DisplayName("Campos null del body no pisan preferencias existentes vía MapStruct")
    void ignoraNullsAlActualizar() throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, "es", true, "09:00-18:00");

        mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"pt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferencias.idioma").value("pt"))
                .andExpect(jsonPath("$.preferencias.notificacionesHabilitadas").value(true))
                .andExpect(jsonPath("$.preferencias.horarioPreferido").value("09:00-18:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"idioma\":\"es\",\"documento\":\"30111222\"}",
        "{\"idioma\":\"es\",\"estado\":\"ACTIVO\"}",
        "{\"notificacionesHabilitadas\":true,\"extra\":true}"
    })
    @DisplayName("Propiedad desconocida en el body → 400 VALIDATION_ERROR sin aplicar cambios")
    void rechazaPropiedadDesconocida(String cuerpo) throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, "en", false, null);

        String respuesta = mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        Vinculo guardado = vinculoRepository.findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(NUMERO)
                .orElseThrow();
        assertThat(guardado.getPreferencias().getIdioma()).isEqualTo("en");
        assertThat(guardado.getPreferencias().isNotificacionesHabilitadas()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-headers", "tipo-vacio", "numero-vacio"})
    @DisplayName("Sin contexto de credencial válida → 401 UNAUTHORIZED")
    void rechazaSinCredencial(String caso) throws Exception {
        guardarVinculo(VinculoEstado.ACTIVO, "es", true, null);
        var solicitud = patch(RUTA, NUMERO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idioma\":\"pt\"}");
        if ("tipo-vacio".equals(caso)) {
            solicitud = solicitud.header(CredencialHeaders.DOCUMENTO_TIPO, "  ")
                    .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222");
        } else if ("numero-vacio".equals(caso)) {
            solicitud = solicitud.header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                    .header(CredencialHeaders.DOCUMENTO_NUMERO, "");
        }

        mockMvc.perform(solicitud)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Vínculo inexistente → 404 NOT_FOUND con contrato de error")
    void rechazaVinculoInexistente() throws Exception {
        String respuesta = mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"es\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(objectMapper.readTree(respuesta).get("timestamp").asText())).isNotNull();
        assertThat(respuesta).doesNotContain(NUMERO, "VinculoNotFoundException");
    }

    @ParameterizedTest
    @EnumSource(value = VinculoEstado.class, names = {"PENDIENTE_VERIFICACION", "BLOQUEADO", "DESVINCULADO"})
    @DisplayName("Vínculo no ACTIVO → 409 CONFLICT")
    void rechazaVinculoNoActivo(VinculoEstado estado) throws Exception {
        guardarVinculo(estado, "es", true, null);

        mockMvc.perform(patch(RUTA, NUMERO)
                        .header(CredencialHeaders.DOCUMENTO_TIPO, "DNI")
                        .header(CredencialHeaders.DOCUMENTO_NUMERO, "30111222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"pt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private Vinculo guardarVinculo(VinculoEstado estado, String idioma, boolean notificaciones,
            String horario) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(NUMERO);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("30111222");
        vinculo.setEstado(estado);
        vinculo.setTerminosAceptados(true);
        vinculo.setFechaAceptacionTerminos(Instant.parse("2026-01-01T10:00:00Z"));
        vinculo.setFechaUltimoCambioEstado(Instant.parse("2026-01-02T10:00:00Z"));
        if (estado == VinculoEstado.ACTIVO) {
            vinculo.setFechaActivacion(Instant.parse("2026-01-02T10:00:00Z"));
        }
        Preferencias preferencias = new Preferencias();
        preferencias.setIdioma(idioma);
        preferencias.setNotificacionesHabilitadas(notificaciones);
        preferencias.setHorarioPreferido(horario);
        vinculo.setPreferencias(preferencias);
        return vinculoRepository.saveAndFlush(vinculo);
    }
}
