package com.bcra.insurance.ws.seguro;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.exception.UnauthorizedException;
import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DenunciaSiniestroIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PolizaRepository polizas;
    @Autowired
    private SiniestroRepository siniestros;
    @MockitoBean
    private CredencialesServiceClient credenciales;

    @BeforeEach
    void autenticar() {
        when(credenciales.validar(nullable(String.class)))
                .thenThrow(new UnauthorizedException("Verificá tu identidad para consultar tus seguros."));
        doReturn(new ValidarCredencialResponse(true, "+5491112345678", "DNI", "12345678",
                Instant.now().plusSeconds(300))).when(credenciales).validar("valida");
    }

    @Test
    @DisplayName("POST crea el caso con número único y responde 201 DenunciaResponse")
    void creaCasoConNumeroUnico() throws Exception {
        Poliza poliza = guardar("POL-1000", "12345678");

        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"HOGAR","fechaSiniestro":"2026-10-20","descripcion":"Daño por agua"}
                """.formatted(poliza.getId());

        String numeroCaso = mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCaso", startsWith("SIN-")))
                .andExpect(jsonPath("$.proximosPasos").isNotEmpty())
                .andExpect(jsonPath("$.plazos").isNotEmpty())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*\"numeroCaso\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        Siniestro guardado = siniestros.findByNumeroCaso(numeroCaso).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoSiniestro.RECIBIDO);
        assertThat(guardado.getPolizaId()).isEqualTo(poliza.getId());
        assertThat(guardado.getTipoSiniestro()).isEqualTo(TipoSiniestro.HOGAR);
        assertThat(guardado.getDescripcion()).isEqualTo("Daño por agua");
        assertThat(guardado.getVentanaDuplicadoHasta()).isAfter(guardado.getFechaApertura());
    }

    @Test
    @DisplayName("Exige los cuatro campos de la denuncia con contrato VALIDATION_ERROR")
    void validaCuatroCampos() throws Exception {
        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasSize(4)))
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(siniestros.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Una denuncia abierta coincidente responde 409 CONFLICT")
    void rechazaDuplicado() throws Exception {
        Poliza poliza = guardar("POL-2000", "12345678");
        Instant ahora = Instant.now();
        siniestros.save(Siniestro.builder()
                .numeroCaso("SIN-EXISTENTE")
                .polizaId(poliza.getId())
                .tipoSiniestro(TipoSiniestro.AUTO)
                .fechaSiniestro(LocalDate.now().minusDays(1))
                .descripcion("Caso previo")
                .estado(EstadoSiniestro.EN_ANALISIS)
                .fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(86400))
                .build());

        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"AUTO","fechaSiniestro":"2026-10-21","descripcion":"Otro choque"}
                """.formatted(poliza.getId());

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("denuncia abierta")))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(siniestros.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un caso resuelto no impide una nueva denuncia de la misma póliza y tipo")
    void permiteTrasResuelto() throws Exception {
        Poliza poliza = guardar("POL-3000", "12345678");
        Instant ahora = Instant.now();
        siniestros.save(Siniestro.builder()
                .numeroCaso("SIN-RESUELTO")
                .polizaId(poliza.getId())
                .tipoSiniestro(TipoSiniestro.VIDA)
                .fechaSiniestro(LocalDate.now().minusDays(5))
                .descripcion("Caso cerrado")
                .estado(EstadoSiniestro.RESUELTO)
                .fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(86400))
                .build());

        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"VIDA","fechaSiniestro":"2026-10-22","descripcion":"Nuevo evento"}
                """.formatted(poliza.getId());

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCaso", startsWith("SIN-")));
        assertThat(siniestros.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Documentación pendiente dentro de la ventana bloquea un segundo caso")
    void rechazaDuplicadoConDocumentacionPendiente() throws Exception {
        Poliza poliza = guardar("POL-3100", "12345678");
        Instant ahora = Instant.now();
        siniestros.save(Siniestro.builder()
                .numeroCaso("SIN-DOC-PEND")
                .polizaId(poliza.getId())
                .tipoSiniestro(TipoSiniestro.HOGAR)
                .fechaSiniestro(LocalDate.now().minusDays(2))
                .descripcion("Caso con docs pendientes")
                .estado(EstadoSiniestro.DOCUMENTACION_PENDIENTE)
                .fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(86400 * 30L))
                .build());

        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"HOGAR","fechaSiniestro":"2026-10-23","descripcion":"Segunda denuncia"}
                """.formatted(poliza.getId());

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(siniestros.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un caso abierto fuera de la ventana de duplicados permite una nueva denuncia")
    void permiteFueraDeVentana() throws Exception {
        Poliza poliza = guardar("POL-3200", "12345678");
        Instant aperturaAntigua = Instant.now().minusSeconds(86400 * 31L);
        siniestros.save(Siniestro.builder()
                .numeroCaso("SIN-ANTIGUO")
                .polizaId(poliza.getId())
                .tipoSiniestro(TipoSiniestro.AUTO)
                .fechaSiniestro(LocalDate.now().minusDays(40))
                .descripcion("Caso antiguo aún abierto")
                .estado(EstadoSiniestro.RECIBIDO)
                .fechaApertura(aperturaAntigua)
                .ventanaDuplicadoHasta(aperturaAntigua.plusSeconds(86400 * 30L))
                .build());

        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"AUTO","fechaSiniestro":"2026-10-24","descripcion":"Nuevo choque"}
                """.formatted(poliza.getId());

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCaso", startsWith("SIN-")));
        assertThat(siniestros.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Descripción en blanco responde VALIDATION_ERROR sin abrir caso")
    void rechazaDescripcionEnBlanco() throws Exception {
        Poliza poliza = guardar("POL-3300", "12345678");
        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"HOGAR","fechaSiniestro":"2026-10-20","descripcion":"   "}
                """.formatted(poliza.getId());

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(siniestros.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Sin credencial responde 401; póliza ajena 403; inexistente 404")
    void respetaAutorizacion() throws Exception {
        Poliza ajena = guardar("POL-4000", "otro");
        String cuerpo = """
                {"polizaId":"%s","tipoSiniestro":"OTROS","fechaSiniestro":"2026-10-20","descripcion":"Evento"}
                """.formatted(ajena.getId());

        mvc.perform(post("/api/v1/siniestros").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String inexistente = """
                {"polizaId":"%s","tipoSiniestro":"OTROS","fechaSiniestro":"2026-10-20","descripcion":"Evento"}
                """.formatted(UUID.randomUUID());
        mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Dos aperturas válidas distintas reciben números de caso diferentes")
    void asignaNumerosDistintos() throws Exception {
        Poliza poliza = guardar("POL-5000", "12345678");
        String hogar = """
                {"polizaId":"%s","tipoSiniestro":"HOGAR","fechaSiniestro":"2026-10-20","descripcion":"Uno"}
                """.formatted(poliza.getId());
        String auto = """
                {"polizaId":"%s","tipoSiniestro":"AUTO","fechaSiniestro":"2026-10-21","descripcion":"Dos"}
                """.formatted(poliza.getId());

        String primero = mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hogar))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String segundo = mvc.perform(post("/api/v1/siniestros")
                        .header("X-Credencial-Sesion", "valida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(auto))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String numero1 = primero.replaceAll("(?s).*\"numeroCaso\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        String numero2 = segundo.replaceAll("(?s).*\"numeroCaso\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(numero1).isNotEqualTo(numero2);
    }

    private Poliza guardar(String numero, String documento) {
        return polizas.save(Poliza.builder().numeroPoliza(numero).ramo("HOGAR").vigente(true)
                .titularDocumentoTipo("DNI").titularDocumentoNumero(documento)
                .fechaVigenciaDesde(LocalDate.now().minusDays(10))
                .fechaVigenciaHasta(LocalDate.now().plusDays(10)).build());
    }
}
