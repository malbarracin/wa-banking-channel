package com.bcra.insurance.ws.seguro;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguimientoSiniestroIntegrationTest {

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
    @DisplayName("GET caso refleja el estado del sistema y la acción pendiente")
    void consultaEstadoDelSistema() throws Exception {
        Poliza poliza = guardarPoliza("12345678");
        guardarSiniestro("SIN-100", poliza.getId(), EstadoSiniestro.DOCUMENTACION_PENDIENTE,
                "Falta documentación", List.of("DNI", "Fotos del daño"));

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-100")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCaso").value("SIN-100"))
                .andExpect(jsonPath("$.estado").value("DOCUMENTACION_PENDIENTE"))
                .andExpect(jsonPath("$.pendiente").value("Falta documentación"))
                .andExpect(jsonPath("$.descripcion").doesNotExist());
    }

    @Test
    @DisplayName("GET documentacion-pendiente enumera documentos y orienta el envío formal")
    void listaDocumentacionPendiente() throws Exception {
        Poliza poliza = guardarPoliza("12345678");
        guardarSiniestro("SIN-200", poliza.getId(), EstadoSiniestro.DOCUMENTACION_PENDIENTE, null,
                List.of("DNI", "Denuncia policial"));

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-200")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos.length()").value(2))
                .andExpect(jsonPath("$.documentos[0]").value("DNI"))
                .andExpect(jsonPath("$.documentos[1]").value("Denuncia policial"))
                .andExpect(jsonPath("$.orientacionEnvio", containsString("canales oficiales")))
                .andExpect(jsonPath("$.orientacionEnvio", containsString("no reemplaza")));
    }

    @Test
    @DisplayName("Deriva pendiente del estado cuando el sistema no informó un texto")
    void derivaPendienteDelEstado() throws Exception {
        Poliza poliza = guardarPoliza("12345678");
        guardarSiniestro("SIN-300", poliza.getId(), EstadoSiniestro.EN_ANALISIS, null, List.of());

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-300")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ANALISIS"))
                .andExpect(jsonPath("$.pendiente").value("Sin acciones pendientes del cliente"));
    }

    @Test
    @DisplayName("GET documentacion-pendiente con lista vacía aún orienta el envío formal")
    void documentacionPendienteSinItems() throws Exception {
        Poliza poliza = guardarPoliza("12345678");
        guardarSiniestro("SIN-250", poliza.getId(), EstadoSiniestro.RESUELTO, null, List.of());

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-250")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos.length()").value(0))
                .andExpect(jsonPath("$.orientacionEnvio", containsString("canales oficiales")));
    }

    @Test
    @DisplayName("Ambos endpoints exigen credencial y mantienen el contrato de error")
    void rechazaSinCredencial() throws Exception {
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Un caso ajeno recibe 403 sin datos del siniestro")
    void rechazaCasoAjeno() throws Exception {
        Poliza ajena = guardarPoliza("99999999");
        guardarSiniestro("SIN-AJENO", ajena.getId(), EstadoSiniestro.RECIBIDO, "secreto", List.of("secreto"));

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-AJENO")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.details").isEmpty()).andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.pendiente").doesNotExist());
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-AJENO")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.documentos").doesNotExist());
    }

    @Test
    @DisplayName("Un caso inexistente recibe 404 con el contrato común")
    void informaCasoInexistente() throws Exception {
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-NOEXISTE")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty()).andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-NOEXISTE")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("El asegurado no titular no opera sobre el caso")
    void rechazaAsegurado() throws Exception {
        Poliza poliza = guardarPoliza("otro");
        poliza.setAseguradoDocumentoTipo("DNI");
        poliza.setAseguradoDocumentoNumero("12345678");
        guardarSiniestro("SIN-ASEG", poliza.getId(), EstadoSiniestro.RECIBIDO, null, List.of());

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-ASEG")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Revalida la credencial en cada consulta de seguimiento")
    void revalidaCredencial() throws Exception {
        Poliza poliza = guardarPoliza("12345678");
        guardarSiniestro("SIN-400", poliza.getId(), EstadoSiniestro.RESUELTO, null, List.of());
        mvc.perform(get("/api/v1/siniestros/{numeroCaso}", "SIN-400")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk());
        when(credenciales.validar("valida"))
                .thenThrow(new UnauthorizedException("Verificá tu identidad para consultar tus seguros."));

        mvc.perform(get("/api/v1/siniestros/{numeroCaso}/documentacion-pendiente", "SIN-400")
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(credenciales, times(2)).validar("valida");
    }

    private Poliza guardarPoliza(String documento) {
        return polizas.save(Poliza.builder().numeroPoliza("POL-" + UUID.randomUUID().toString().substring(0, 8))
                .ramo("HOGAR").vigente(true).titularDocumentoTipo("DNI").titularDocumentoNumero(documento)
                .fechaVigenciaDesde(LocalDate.now().minusDays(10))
                .fechaVigenciaHasta(LocalDate.now().plusDays(10)).build());
    }

    private Siniestro guardarSiniestro(String numeroCaso, UUID polizaId, EstadoSiniestro estado, String pendiente,
            List<String> documentos) {
        Instant ahora = Instant.now();
        return siniestros.save(Siniestro.builder().numeroCaso(numeroCaso).polizaId(polizaId)
                .tipoSiniestro(TipoSiniestro.HOGAR).fechaSiniestro(LocalDate.now().minusDays(1))
                .descripcion("Daño en el hogar").estado(estado).pendiente(pendiente)
                .documentosPendientes(documentos).fechaApertura(ahora)
                .ventanaDuplicadoHasta(ahora.plusSeconds(3600)).build());
    }
}
