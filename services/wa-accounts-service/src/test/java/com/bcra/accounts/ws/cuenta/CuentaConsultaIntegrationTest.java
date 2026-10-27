package com.bcra.accounts.ws.cuenta;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaAccion;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;
import com.bcra.accounts.ws.cuenta.repository.AuditoriaCuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CuentaConsultaIntegrationTest {

    private static final String CREDENCIAL = "sesion-validada";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private AuditoriaCuentaRepository auditoriaCuentaRepository;

    @MockitoBean
    private CredencialesServiceClient credencialesServiceClient;

    @BeforeEach
    void prepararCuentas() {
        auditoriaCuentaRepository.deleteAll();
        cuentaRepository.deleteAll();
        guardarCuenta("propia", "DNI", "123");
        guardarCuenta("ajena", "DNI", "456");
        guardarCuenta("otro-tipo", "PASAPORTE", "123");
        when(credencialesServiceClient.validar(CREDENCIAL)).thenReturn(new ValidarCredencialResponse(
                true, "+5491112345678", "DNI", "123", Instant.now().plusSeconds(600)));
    }

    @Test
    @DisplayName("Lista solo las cuentas del documento validado y audita el listado")
    void listaSoloCuentasDelTitular() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numeroCuenta").value("propia"))
                .andExpect(jsonPath("$[0].alias").value("principal"))
                .andExpect(jsonPath("$[0].saldoDisponible").doesNotExist())
                .andExpect(jsonPath("$[0].titularDocumentoNumero").doesNotExist());

        assertThat(auditoriaCuentaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.LISTAR_CUENTAS);
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.EXITOSO);
        });
    }

    @Test
    @DisplayName("Devuelve ambos saldos y la moneda de la cuenta propia")
    void consultaSaldoPropio() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/propia/saldo").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponible").value(100.25))
                .andExpect(jsonPath("$.saldoContable").value(120.50))
                .andExpect(jsonPath("$.moneda").value("ARS"));

        assertThat(auditoriaCuentaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getAccion()).isEqualTo(AuditoriaAccion.CONSULTAR_SALDO);
            assertThat(auditoria.getCuentaId()).isNotNull();
        });
    }

    @Test
    @DisplayName("Una cuenta ajena recibe 404 y conserva la auditoría de rechazo")
    void rechazaCuentaAjena() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/ajena/saldo").header("X-Credencial-Sesion", CREDENCIAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(auditoriaCuentaRepository.findAll()).singleElement().satisfies(auditoria -> {
            assertThat(auditoria.getResultado()).isEqualTo(AuditoriaResultado.RECHAZADO);
            assertThat(auditoria.getCuentaId()).isNull();
        });
    }

    @Test
    @DisplayName("Sin credencial se rechazan ambas consultas con el contrato de error")
    void rechazaConsultasSinCredencial() throws Exception {
        when(credencialesServiceClient.validar("")).thenThrow(new UnauthorizedException("Credencial no válida"));

        mockMvc.perform(get("/api/v1/cuentas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(get("/api/v1/cuentas/propia/saldo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
        assertThat(auditoriaCuentaRepository.count()).isZero();
    }

    private void guardarCuenta(String numero, String documentoTipo, String documentoNumero) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta(numero);
        cuenta.setAlias("principal");
        cuenta.setTipo(TipoCuenta.CA);
        cuenta.setMoneda(Moneda.ARS);
        cuenta.setTitularDocumentoTipo(documentoTipo);
        cuenta.setTitularDocumentoNumero(documentoNumero);
        cuenta.setSaldoDisponible(new BigDecimal("100.25"));
        cuenta.setSaldoContable(new BigDecimal("120.50"));
        cuenta.setActiva(true);
        cuentaRepository.saveAndFlush(cuenta);
    }
}
