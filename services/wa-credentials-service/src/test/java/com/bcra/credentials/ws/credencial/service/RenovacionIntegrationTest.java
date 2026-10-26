package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;
import com.bcra.credentials.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"credencial.vigencia-minutos=120", "credencial.renovacion-ventana-minutos=15"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RenovacionIntegrationTest {

    private static final String NUMERO = "+5491123456789";
    private static final long VIGENCIA_SEGUNDOS = 120 * 60;

    @Autowired
    private RenovacionService service;
    @Autowired
    private ValidacionService validacionService;
    @MockitoSpyBean
    private CredencialAuditLogRepository auditRepository;
    @Autowired
    private MockMvc mockMvc;
    @MockitoSpyBean
    private CredencialRepository repository;
    @MockitoSpyBean
    private JwtEncoder jwtEncoder;
    @MockitoBean
    private UsersServiceClient usersServiceClient;

    @BeforeEach
    void limpiarPersistencia() {
        repository.deleteAll();
        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("Rota una próxima a expirar, conserva vínculo y firma JWT sin repetir la verificación")
    void renuevaAtomicamenteConVigenciaConfigurada() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);

        service.renovarProximasAExpirar();

        CredencialSesion revocada = repository.findById(anterior.getId()).orElseThrow();
        assertThat(revocada.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
        assertThat(revocada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REEMPLAZO);
        assertThat(revocada.getRevocadaPor()).isEqualTo(RevocadaPor.SISTEMA);
        assertThat(revocada.getFechaRevocacion()).isNotNull();
        CredencialSesion nueva = repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)
                .orElseThrow();
        assertThat(nueva.getJti()).isNotEqualTo(anterior.getJti());
        assertThat(nueva.getVinculoId()).isEqualTo(anterior.getVinculoId());
        assertThat(nueva.getFechaExpiracion()).isEqualTo(nueva.getFechaEmision().plusSeconds(VIGENCIA_SEGUNDOS));
        assertThat(nueva.getFechaRevocacion()).isNull();
        assertThat(nueva.getMotivoRevocacion()).isNull();
        assertThat(nueva.isRiesgo()).isFalse();
        var parametros = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parametros.capture());
        assertThat(parametros.getValue().getClaims().getId()).isEqualTo(nueva.getJti());
        assertThat(parametros.getValue().getClaims().getSubject()).isEqualTo(NUMERO);
        assertThat(auditRepository.findAll()).hasSize(2).anySatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.RENOVACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(evento.getJti()).isEqualTo(nueva.getJti());
        });
        verifyNoInteractions(usersServiceClient);
        service.renovarProximasAExpirar();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("El riesgo impide renovar y validar, y deja el marcador para re-verificación")
    void derivaRiesgoSinRevocar() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, true);

        service.renovarProximasAExpirar();

        assertThat(repository.findAll()).singleElement().satisfies(credencial -> {
            assertThat(credencial.isRiesgo()).isTrue();
            assertThat(credencial.getEstado()).isEqualTo(CredencialEstado.ACTIVA);
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.RENOVACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
            assertThat(evento.getMotivo()).isEqualTo("REVERIFICACION_REQUERIDA");
        });
        verifyNoInteractions(jwtEncoder, usersServiceClient);
        String token = firmar(anterior);
        assertThatThrownBy(() -> validacionService.validar(token)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(usersServiceClient);
    }

    @Test
    @DisplayName("Ignora expiradas, revocadas y credenciales fuera de la ventana configurada")
    void omiteNoElegibles() {
        guardar(-60, CredencialEstado.ACTIVA, false);
        guardar(600, CredencialEstado.REVOCADA, false);
        guardar(600, CredencialEstado.EXPIRADA, false);
        guardar(1800, CredencialEstado.ACTIVA, false);

        service.renovarProximasAExpirar();

        assertThat(repository.count()).isEqualTo(4);
        assertThat(auditRepository.count()).isZero();
        verifyNoInteractions(jwtEncoder, usersServiceClient);
    }

    @Test
    @DisplayName("Una falla de inserción revierte el reemplazo y persiste solo el rechazo independiente")
    void revierteRotacionSiFallaInsercion() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        doThrow(new DataIntegrityViolationException("detalle protegido"))
                .when(repository).saveAndFlush(any(CredencialSesion.class));

        assertThatThrownBy(service::renovarProximasAExpirar).isInstanceOf(DataIntegrityViolationException.class);

        verificarRollback(anterior);
    }

    @Test
    @DisplayName("Una falla de firma conserva la credencial anterior y audita el rechazo")
    void conservaAnteriorSiFallaFirma() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        doThrow(new JwtEncodingException("detalle protegido"))
                .when(jwtEncoder).encode(any(JwtEncoderParameters.class));

        assertThatThrownBy(service::renovarProximasAExpirar).isInstanceOf(JwtEncodingException.class);

        verificarRollback(anterior);
    }

    @Test
    @DisplayName("Revalida el estado bajo bloqueo antes de renovar una candidata revocada concurrentemente")
    void omiteCandidataRevocadaAntesDelBloqueo() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        anterior.setEstado(CredencialEstado.REVOCADA);
        doReturn(Optional.of(anterior)).when(repository).buscarParaRenovar(anterior.getId());

        service.renovarProximasAExpirar();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(auditRepository.count()).isZero();
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    @DisplayName("El JWT anterior deja de autorizar por HTTP después de confirmar la renovación")
    void rechazaJwtAnteriorTrasRenovar() throws Exception {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);

        service.renovarProximasAExpirar();

        verificarRechazoHttp(token);
        verifyNoInteractions(usersServiceClient);
    }

    @Test
    @DisplayName("El riesgo deriva al canal mediante un rechazo HTTP con el contrato de errores")
    void rechazaPorHttpCredencialConRiesgo() throws Exception {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, true);
        String token = firmar(anterior);

        service.renovarProximasAExpirar();

        verificarRechazoHttp(token);
        verifyNoInteractions(jwtEncoder, usersServiceClient);
    }

    @Test
    @DisplayName("Una falla en la auditoría exitosa revierte también la nueva credencial y la revocación")
    void revierteRotacionSiFallaAuditoriaExitosa() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        doThrow(new DataIntegrityViolationException("detalle protegido")).when(auditRepository)
                .save(argThat(evento -> evento.getAccion() == AuditAccion.RENOVACION
                        && evento.getResultado() == AuditResultado.EXITOSO));

        assertThatThrownBy(service::renovarProximasAExpirar).isInstanceOf(DataIntegrityViolationException.class);

        verificarRollback(anterior);
    }

    private void verificarRechazoHttp(String token) throws Exception {
        mockMvc.perform(post("/api/v1/credenciales/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private CredencialSesion guardar(long segundosRestantes, CredencialEstado estado, boolean riesgo) {
        Instant ahora = Instant.now();
        return repository.saveAndFlush(CredencialSesion.builder().jti(UUID.randomUUID().toString())
                .numeroWhatsapp(NUMERO).documentoTipo("DNI").documentoNumero("12345678")
                .vinculoId(UUID.randomUUID()).estado(estado).riesgo(riesgo)
                .fechaEmision(ahora.minusSeconds(3600)).fechaExpiracion(ahora.plusSeconds(segundosRestantes))
                .build());
    }

    private String firmar(CredencialSesion sesion) {
        JwtClaimsSet claims = JwtClaimsSet.builder().id(sesion.getJti()).subject(NUMERO)
                .claim("doc", "DNI:12345678").issuedAt(sesion.getFechaEmision())
                .expiresAt(sesion.getFechaExpiracion()).build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        clearInvocations(jwtEncoder);
        return jwt.getTokenValue();
    }

    private void verificarRollback(CredencialSesion anterior) {
        assertThat(repository.findAll()).singleElement().satisfies(credencial -> {
            assertThat(credencial.getJti()).isEqualTo(anterior.getJti());
            assertThat(credencial.getEstado()).isEqualTo(CredencialEstado.ACTIVA);
            assertThat(credencial.getFechaRevocacion()).isNull();
        });
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.RENOVACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
        });
    }
}
