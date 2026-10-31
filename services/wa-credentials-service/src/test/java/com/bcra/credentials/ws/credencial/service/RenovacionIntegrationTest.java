package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.Optional;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse;
import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.repository.EntregaRenovacionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @Autowired
    private EntregaRenovacionRepository entregas;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CredencialMapper mapper;
    @Autowired
    private CredencialAuditService auditService;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void limpiarPersistencia() {
        entregas.deleteAll();
        repository.deleteAll();
        auditRepository.deleteAll();
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.ACTIVE));
    }

    @Test
    @DisplayName("Rota una próxima a expirar, conserva vínculo y firma JWT sin repetir la verificación")
    void renuevaAtomicamenteConVigenciaConfigurada() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);

        renovarYConfirmar(anterior);

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
        verify(jwtEncoder, times(1)).encode(parametros.capture());
        assertThat(parametros.getValue().getClaims().getId()).isEqualTo(nueva.getJti());
        assertThat(parametros.getValue().getClaims().getSubject()).isEqualTo(NUMERO);
        assertThat(auditRepository.findAll()).hasSize(2).anySatisfy(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.RENOVACION);
            assertThat(evento.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            assertThat(evento.getJti()).isEqualTo(nueva.getJti());
        });
        verify(usersServiceClient, times(2)).consultarEstado("DNI", "12345678");
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

        assertThatThrownBy(() -> renovarYConfirmar(anterior)).isInstanceOf(DataIntegrityViolationException.class);

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

        renovarYConfirmar(anterior);

        verificarRechazoHttp(token);
        verify(usersServiceClient, times(2)).consultarEstado("DNI", "12345678");
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

        assertThatThrownBy(() -> renovarYConfirmar(anterior)).isInstanceOf(DataIntegrityViolationException.class);

        verificarRollback(anterior);
    }

    @Test
    @DisplayName("La revocación individual tras renovar afecta la nueva credencial y evita otra renovación")
    void conservaRevocacionIndividualTrasRenovar() throws Exception {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        renovarYConfirmar(anterior);
        CredencialSesion nueva = repository.findFirstByNumeroWhatsappOrderByFechaEmisionDesc(NUMERO)
                .orElseThrow();
        assertThat(nueva.getJti()).isNotEqualTo(anterior.getJti());

        mockMvc.perform(post("/api/v1/credenciales/{numero}/revocacion", NUMERO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"BLOQUEO\",\"solicitadoPor\":\"CANAL\"}"))
                .andExpect(status().isNoContent());
        service.renovarProximasAExpirar();

        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, CredencialEstado.ACTIVA)).isEmpty();
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(nueva.getId())).hasValueSatisfying(credencial -> {
            assertThat(credencial.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
            assertThat(credencial.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.BLOQUEO);
        });
        assertThat(repository.findById(anterior.getId())).hasValueSatisfying(credencial ->
                assertThat(credencial.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REEMPLAZO));
        verify(usersServiceClient, times(2)).consultarEstado("DNI", "12345678");
    }

    private void renovarYConfirmar(CredencialSesion anterior) {
        service.renovarProximasAExpirar();
        String token = firmar(anterior);
        String nuevo = service.obtener(token).credencial();
        service.confirmar(nuevo);
    }

    @Test
    @DisplayName("Entrega repetible por HTTP sin revocar antes del acuse; confirmar dos veces es seguro")
    void entregaRepetibleYConfirmacionIdempotente() throws Exception {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        service.renovarProximasAExpirar();
        assertThat(entregas.count()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(validacionService.validar(token).vigente()).isTrue();
        String respuesta = mockMvc.perform(post("/api/v1/credenciales/renovacion/obtener")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("credencial", token))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String nuevo = json.readTree(respuesta).get("credencial").asText();
        assertThat(service.obtener(token).credencial()).isEqualTo(nuevo);
        assertThat(validacionService.validar(token).vigente()).isTrue();
        assertThatThrownBy(() -> validacionService.validar(nuevo)).isInstanceOf(UnauthorizedException.class);
        for (int intento = 0; intento < 2; intento++) {
            mockMvc.perform(post("/api/v1/credenciales/renovacion/confirmar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("credencial", nuevo))))
                    .andExpect(status().isNoContent());
        }
        assertThat(validacionService.validar(nuevo).vigente()).isTrue();
        verificarRechazoHttp(token);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(auditRepository.findAll().stream().filter(evento -> evento.getAccion() == AuditAccion.RENOVACION
                && evento.getResultado() == AuditResultado.EXITOSO)).hasSize(1);
    }

    @Test
    @DisplayName("Revocar mientras H3 obtiene el reemplazo impide activarlo")
    void revocacionPrevaleceSobreEntregaPendiente() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        String nuevo = service.obtener(token).credencial();
        anterior.setEstado(CredencialEstado.REVOCADA);
        anterior.setFechaRevocacion(Instant.now());
        repository.saveAndFlush(anterior);
        assertThatThrownBy(() -> service.obtener(token)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.confirmar(nuevo)).isInstanceOf(UnauthorizedException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una instancia nueva de H2 recupera y confirma la misma entrega persistida")
    void recuperaEntregaTrasReinicio() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        String nuevo = service.obtener(token).credencial();
        RenovacionService reiniciado = new RenovacionService(repository, auditRepository, auditService, mapper,
                jwtEncoder, transactionTemplate, entregas, jwtService, validacionService);
        assertThat(reiniciado.obtener(token).credencial()).isEqualTo(nuevo);
        reiniciado.confirmar(nuevo);
        assertThat(validacionService.validar(nuevo).vigente()).isTrue();
        assertThatThrownBy(() -> validacionService.validar(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("El riesgo detectado después de preparar la entrega impide activarla")
    void riesgoPosteriorImpideConfirmacion() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        String nuevo = service.obtener(token).credencial();
        anterior.setRiesgo(true);
        repository.saveAndFlush(anterior);
        assertThatThrownBy(() -> service.confirmar(nuevo)).isInstanceOf(UnauthorizedException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una falla de firma durante la entrega conserva la anterior y los metadatos para reintentar")
    void fallaDeEntregaNoRevoca() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        doThrow(new JwtEncodingException("detalle protegido"))
                .when(jwtEncoder).encode(any(JwtEncoderParameters.class));
        assertThatThrownBy(() -> service.obtener(token)).isInstanceOf(JwtEncodingException.class);
        assertThat(validacionService.validar(token).vigente()).isTrue();
        assertThat(entregas.count()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("H1 suspendido impide activar el reemplazo sin perder la credencial anterior")
    void clienteSuspendidoImpideConfirmar() {
        CredencialSesion anterior = guardar(600, CredencialEstado.ACTIVA, false);
        String token = firmar(anterior);
        service.renovarProximasAExpirar();
        String nuevo = service.obtener(token).credencial();
        when(usersServiceClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.SUSPENDED));
        assertThatThrownBy(() -> service.confirmar(nuevo)).isInstanceOf(UnauthorizedException.class);
        assertThat(repository.findById(anterior.getId())).hasValueSatisfying(credencial ->
                assertThat(credencial.getEstado()).isEqualTo(CredencialEstado.ACTIVA));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Endpoints rechazan cuerpos vacíos, tokens falsos y entregas todavía no preparadas")
    void validaContratoDeEntrega() throws Exception {
        for (String accion : new String[] {"obtener", "confirmar"}) {
            mockMvc.perform(post("/api/v1/credenciales/renovacion/" + accion)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(post("/api/v1/credenciales/renovacion/" + accion)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"credencial\":\"falso\"}"))
                    .andExpect(status().isUnauthorized());
        }
        String token = firmar(guardar(600, CredencialEstado.ACTIVA, false));
        mockMvc.perform(post("/api/v1/credenciales/renovacion/obtener")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("credencial", token))))
                .andExpect(status().isNotFound());
        assertThat(validacionService.validar(token).vigente()).isTrue();
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
