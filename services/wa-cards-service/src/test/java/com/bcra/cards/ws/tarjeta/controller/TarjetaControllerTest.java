package com.bcra.cards.ws.tarjeta.controller;

import com.bcra.cards.ws.exception.ForbiddenException;
import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaAccion;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaResultado;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import com.bcra.cards.ws.tarjeta.repository.AuditoriaTarjetaRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import com.bcra.cards.ws.tarjeta.service.TarjetaService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TarjetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredencialesServiceClient credencialesClient;

    @MockitoSpyBean
    private TarjetaRepository tarjetaRepository;

    @Autowired
    private AuditoriaTarjetaRepository auditoriaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TarjetaService tarjetaService;

    @BeforeEach
    void preparar() {
        tarjetaRepository.deleteAll();
        auditoriaRepository.deleteAll();
        clearInvocations(tarjetaRepository);
        when(credencialesClient.validar("sesion-valida")).thenReturn(new ValidarCredencialResponse(
                true, "+5491100000000", "DNI", "12345678", Instant.now().plusSeconds(300)));
    }

    @Test
    @DisplayName("Lista solo tarjetas del documento validado, sin datos sensibles y con auditoría persistida")
    void listaSoloTarjetasDelTitular() throws Exception {
        var propia = guardarTarjeta("DNI", "12345678");
        guardarTarjeta("DNI", "87654321");
        guardarTarjeta("PASAPORTE", "12345678");

        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "sesion-valida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(propia.getId().toString()))
                .andExpect(jsonPath("$[0].ultimosCuatro").value("1234"))
                .andExpect(jsonPath("$[0].marca").value("VISA"))
                .andExpect(jsonPath("$[0].tipo").value("CREDITO"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$[0].length()").value(5));

        verify(credencialesClient).validar("sesion-valida");
        assertThat(auditoriaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.LISTAR_TARJETAS);
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
            assertThat(auditoria.getDocumentoNumero()).isEqualTo("12345678");
            assertThat(auditoria.getNumeroWhatsapp()).isEqualTo("+5491100000000");
            assertThat(auditoria.getFecha()).isNotNull();
        });
    }

    @Test
    @DisplayName("Un titular sin tarjetas recibe una lista vacía y su consulta se audita")
    void listaVacia() throws Exception {
        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "sesion-valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertThat(auditoriaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin credencial o con credencial vacía devuelve 401 sin consultar productos")
    void rechazaAusenciaDeCredencial() throws Exception {
        mockMvc.perform(get("/api/v1/tarjetas"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").isString());
        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", " "))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(credencialesClient, tarjetaRepository);
    }

    @Test
    @DisplayName("La credencial rechazada y el vínculo inactivo no permiten consultar productos")
    void rechazaAntesDeConsultar() throws Exception {
        when(credencialesClient.validar("revocada")).thenThrow(new UnauthorizedException("Credencial no válida"));
        when(credencialesClient.validar("inactiva")).thenThrow(new ForbiddenException("Vínculo inactivo"));
        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "revocada"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "inactiva"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(tarjetaRepository, never())
                .findByTitularDocumentoTipoAndTitularDocumentoNumero(anyString(), anyString());
        assertThat(auditoriaRepository.count()).isZero();
    }

    @Test
    @DisplayName("Una revocación posterior al listado impide volver a consultar productos con la misma credencial")
    void rechazaRevocacionTrasListadoExitoso() throws Exception {
        guardarTarjeta("DNI", "12345678");
        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "sesion-valida"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        clearInvocations(credencialesClient, tarjetaRepository);
        when(credencialesClient.validar("sesion-valida"))
                .thenThrow(new UnauthorizedException("Credencial no válida"));

        mockMvc.perform(get("/api/v1/tarjetas").header("X-Credencial-Sesion", "sesion-valida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Credencial no válida"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isString());

        verify(credencialesClient).validar("sesion-valida");
        verifyNoInteractions(tarjetaRepository);
        assertThat(auditoriaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("La auditoría del listado permanece confirmada aunque la transacción externa se revierta")
    void conservaAuditoriaTrasRollbackExterno() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaccion -> {
            assertThat(tarjetaService.listar("sesion-valida")).isEmpty();
            transaccion.setRollbackOnly();
        });

        assertThat(auditoriaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.LISTAR_TARJETAS);
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
            assertThat(auditoria.getDocumentoTipo()).isEqualTo("DNI");
            assertThat(auditoria.getDocumentoNumero()).isEqualTo("12345678");
        });
    }

    private Tarjeta guardarTarjeta(String tipo, String numero) {
        var tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        tarjeta.setTitularDocumentoTipo(tipo);
        tarjeta.setTitularDocumentoNumero(numero);
        return tarjetaRepository.saveAndFlush(tarjeta);
    }
}
