package com.bcra.insurance.ws.seguro;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.exception.UnauthorizedException;
import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.entity.Cobertura;
import com.bcra.insurance.ws.seguro.entity.Exclusion;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.repository.CoberturaRepository;
import com.bcra.insurance.ws.seguro.repository.ExclusionRepository;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultaSegurosIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PolizaRepository polizas;
    @Autowired
    private CoberturaRepository coberturas;
    @Autowired
    private ExclusionRepository exclusiones;
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
    @DisplayName("Lista solo pólizas vigentes como titular o asegurado con identificador corto")
    void listaPolizasAutorizadas() throws Exception {
        Poliza titular = guardar("POL-1234", "12345678", true);
        Poliza asegurado = guardar("POL-5678", "otro", true);
        asegurado.setAseguradoDocumentoTipo("DNI");
        asegurado.setAseguradoDocumentoNumero("12345678");
        guardar("POL-0000", "otro", true);
        guardar("POL-0001", "12345678", false);
        Poliza vencida = guardar("POL-0002", "12345678", true);
        vencida.setFechaVigenciaHasta(LocalDate.now().minusDays(1));
        Poliza futura = guardar("POL-0003", "12345678", true);
        futura.setFechaVigenciaDesde(LocalDate.now().plusDays(1));

        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(titular.getId().toString()))
                .andExpect(jsonPath("$[0].identificadorCorto").value("1234"))
                .andExpect(jsonPath("$[0].titularDocumentoNumero").doesNotExist());
    }

    @Test
    @DisplayName("Devuelve resumen y exclusiones sin divulgar el texto completo")
    void consultaResumen() throws Exception {
        Poliza poliza = guardar("P-1", "12345678", true);
        coberturas.save(Cobertura.builder().poliza(poliza).descripcion("Texto legal completo")
                .resumen("Incendio del hogar").build());
        exclusiones.save(Exclusion.builder().poliza(poliza).descripcion("Daño intencional").build());

        mvc.perform(get("/api/v1/seguros/{id}/coberturas", poliza.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coberturas[0]").value("Incendio del hogar"))
                .andExpect(jsonPath("$.exclusiones[0]").value("Daño intencional"))
                .andExpect(jsonPath("$.resumen").value(true))
                .andExpect(jsonPath("$.notaPrevalencia").value(org.hamcrest.Matchers.containsString("oficiales")));
    }

    @Test
    @DisplayName("El asegurado puede consultar coberturas vacías")
    void permiteAsegurado() throws Exception {
        Poliza poliza = guardar("P-1", "otro", true);
        poliza.setAseguradoDocumentoTipo("DNI");
        poliza.setAseguradoDocumentoNumero("12345678");

        mvc.perform(get("/api/v1/seguros/{id}/coberturas", poliza.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coberturas").isEmpty())
                .andExpect(jsonPath("$.exclusiones").isEmpty());
    }

    @Test
    @DisplayName("Una sesión válida sin pólizas recibe una lista vacía")
    void devuelveListaVacia() throws Exception {
        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Ambos endpoints requieren credencial y mantienen el contrato de error")
    void rechazaSinCredencial() throws Exception {
        mvc.perform(get("/api/v1/seguros"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray()).andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/v1/seguros/{id}/coberturas", UUID.randomUUID()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Un tercero recibe 403 sin datos de la póliza")
    void rechazaTerceros() throws Exception {
        Poliza ajena = guardar("P-1", "otro", true);

        mvc.perform(get("/api/v1/seguros/{id}/coberturas", ajena.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.details").isEmpty()).andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.coberturas").doesNotExist());
    }

    @Test
    @DisplayName("Una póliza inexistente devuelve 404 con el contrato común")
    void informaPolizaInexistente() throws Exception {
        mvc.perform(get("/api/v1/seguros/{id}/coberturas", UUID.randomUUID())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isEmpty()).andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Incluye las pólizas cuya vigencia comienza o termina hoy")
    void incluyeLimitesDeVigencia() throws Exception {
        LocalDate hoy = LocalDate.now();
        Poliza comienzaHoy = guardar("POL-1000", "12345678", true);
        comienzaHoy.setFechaVigenciaDesde(hoy);
        Poliza terminaHoy = guardar("POL-2000", "12345678", true);
        terminaHoy.setFechaVigenciaHasta(hoy);

        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(comienzaHoy.getId().toString()))
                .andExpect(jsonPath("$[0].ramo").value("HOGAR"))
                .andExpect(jsonPath("$[0].numeroPoliza").value("POL-1000"))
                .andExpect(jsonPath("$[0].fechaVigenciaDesde").value(hoy.toString()))
                .andExpect(jsonPath("$[1].id").value(terminaHoy.getId().toString()))
                .andExpect(jsonPath("$[1].fechaVigenciaHasta").value(hoy.toString()));
    }

    @ParameterizedTest
    @CsvSource({"PASAPORTE,12345678,DNI,otro", "DNI,otro,PASAPORTE,12345678",
            "PASAPORTE,12345678,PASAPORTE,12345678"})
    @DisplayName("Exige coincidencia de tipo y número en una misma relación autorizada")
    void rechazaCoincidenciasParciales(String tipoTitular, String numeroTitular,
            String tipoAsegurado, String numeroAsegurado) throws Exception {
        Poliza poliza = guardar("POL-1000", numeroTitular, true);
        poliza.setTitularDocumentoTipo(tipoTitular);
        poliza.setAseguradoDocumentoTipo(tipoAsegurado);
        poliza.setAseguradoDocumentoNumero(numeroAsegurado);

        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/seguros/{id}/coberturas", poliza.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Revalida la credencial en cada consulta y rechaza una sesión revocada")
    void rechazaSesionRevocadaTrasConsultaExitosa() throws Exception {
        Poliza poliza = guardar("POL-1000", "12345678", true);
        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk());
        when(credenciales.validar("valida"))
                .thenThrow(new UnauthorizedException("Verificá tu identidad para consultar tus seguros."));

        mvc.perform(get("/api/v1/seguros").header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/v1/seguros/{id}/coberturas", poliza.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.coberturas").doesNotExist());
        verify(credenciales, times(3)).validar("valida");
    }

    @Test
    @DisplayName("El resumen contiene únicamente coberturas y exclusiones de la póliza seleccionada")
    void aislaResumenPorPoliza() throws Exception {
        Poliza propia = guardar("POL-1000", "12345678", true);
        Poliza ajena = guardar("POL-2000", "otro", true);
        coberturas.save(Cobertura.builder().poliza(propia).descripcion("Condiciones completas")
                .resumen("Incendio").build());
        coberturas.save(Cobertura.builder().poliza(ajena).descripcion("Condiciones ajenas")
                .resumen("Cobertura ajena").build());
        exclusiones.save(Exclusion.builder().poliza(propia).descripcion("Daño intencional").build());
        exclusiones.save(Exclusion.builder().poliza(ajena).descripcion("Exclusión ajena").build());

        mvc.perform(get("/api/v1/seguros/{id}/coberturas", propia.getId())
                .header("X-Credencial-Sesion", "valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coberturas.length()").value(1))
                .andExpect(jsonPath("$.coberturas[0]").value("Incendio"))
                .andExpect(jsonPath("$.exclusiones.length()").value(1))
                .andExpect(jsonPath("$.exclusiones[0]").value("Daño intencional"));
    }

    private Poliza guardar(String numero, String documento, boolean vigente) {
        return polizas.save(Poliza.builder().numeroPoliza(numero).ramo("HOGAR").vigente(vigente)
                .titularDocumentoTipo("DNI").titularDocumentoNumero(documento)
                .fechaVigenciaDesde(LocalDate.now().minusDays(10))
                .fechaVigenciaHasta(LocalDate.now().plusDays(10)).build());
    }
}
