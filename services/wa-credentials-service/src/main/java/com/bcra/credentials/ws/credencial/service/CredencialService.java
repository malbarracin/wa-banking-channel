package com.bcra.credentials.ws.credencial.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.client.ClienteNoEncontradoException;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.client.UsersServiceUnavailableException;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialRequest;
import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.dto.RevocarCredencialRequest;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.exception.EmisionNoAutorizadaException;
import com.bcra.credentials.ws.credencial.exception.CredencialNoEncontradaException;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import lombok.RequiredArgsConstructor;

/** Emite y revoca credenciales de forma atómica con su auditoría de éxito. */
@Service
@RequiredArgsConstructor
public class CredencialService {

    private final UsersServiceClient usersServiceClient;
    private final CredencialRepository repository;
    private final CredencialAuditLogRepository auditRepository;
    private final CredencialAuditService auditService;
    private final CredencialMapper mapper;
    private final JwtEncoder jwtEncoder;
    private final TransactionTemplate transactionTemplate;

    @Value("${credencial.vigencia-minutos}")
    private long vigenciaMinutos;

    /** Confirma revocación y auditoría juntas; el rechazo se conserva en transacción independiente. */
    public void revocar(String numero, RevocarCredencialRequest request) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CredencialSesion sesion = repository.findActivaParaRevocar(numero)
                        .orElseThrow(CredencialNoEncontradaException::new);
                sesion.setEstado(CredencialEstado.REVOCADA);
                sesion.setFechaRevocacion(Instant.now());
                sesion.setMotivoRevocacion(request.motivo());
                sesion.setRevocadaPor(request.solicitadoPor());
                registrarExito(sesion, AuditAccion.REVOCACION, request.motivo().name());
                repository.flush();
            });
        } catch (CredencialNoEncontradaException | DataAccessException exception) {
            auditService.registrarRevocacionRechazada(numero, request.motivo());
            throw exception;
        }
    }

    /**
     * Valida confirmaciones y H1 antes de modificar la credencial existente.
     * @return JWT y expiración destinados exclusivamente a H3
     */
    public EmitirCredencialResponse emitir(EmitirCredencialRequest request) {
        try {
            if (!Boolean.TRUE.equals(request.vinculoActivo())
                    || !Boolean.TRUE.equals(request.identidadVerificada())) {
                throw new EmisionNoAutorizadaException();
            }
            if (usersServiceClient.consultarEstado(request.documentoTipo(), request.documentoNumero()).estado()
                    != EstadoCliente.ACTIVE) {
                throw new EmisionNoAutorizadaException();
            }
            if (vigenciaMinutos <= 0) {
                throw new IllegalArgumentException("La vigencia debe ser positiva");
            }
            return Objects.requireNonNull(transactionTemplate.execute(status -> emitirEnTransaccion(request)));
        } catch (EmisionNoAutorizadaException | ClienteNoEncontradoException | UsersServiceUnavailableException
                | DataAccessException | JwtEncodingException | IllegalArgumentException exception) {
            auditService.registrar(AuditAccion.EMISION, AuditResultado.RECHAZADO, request.numeroWhatsapp(),
                    request.documentoTipo(), request.documentoNumero(), "Emisión rechazada");
            throw exception;
        }
    }

    private EmitirCredencialResponse emitirEnTransaccion(EmitirCredencialRequest request) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();
        Instant expiracion = ahora.plus(vigenciaMinutos, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(request.numeroWhatsapp())
                .claim("doc", request.documentoTipo() + ":" + request.documentoNumero())
                .issuedAt(ahora).expiresAt(expiracion).id(jti).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        repository.findByNumeroWhatsappAndEstado(request.numeroWhatsapp(), CredencialEstado.ACTIVA)
                .ifPresent(anterior -> {
                    anterior.setEstado(CredencialEstado.REVOCADA);
                    anterior.setFechaRevocacion(ahora);
                    anterior.setMotivoRevocacion(MotivoRevocacion.REEMPLAZO);
                    anterior.setRevocadaPor(RevocadaPor.SISTEMA);
                    registrarExito(anterior, AuditAccion.REVOCACION, "REEMPLAZO");
                });
        // Actualiza antes del INSERT para respetar el índice único parcial de PostgreSQL.
        repository.flush();
        // Referencia interna del vínculo: el contrato de H3 no proporciona su UUID.
        UUID vinculoId = UUID.nameUUIDFromBytes((request.numeroWhatsapp() + "|" + request.documentoTipo()
                + "|" + request.documentoNumero()).getBytes(StandardCharsets.UTF_8));
        CredencialSesion nueva = repository.saveAndFlush(
                mapper.toEntity(request, jti, vinculoId, ahora, expiracion));
        registrarExito(nueva, AuditAccion.EMISION, "EMISION");
        return mapper.toResponse(nueva, token);
    }

    private void registrarExito(CredencialSesion sesion, AuditAccion accion, String motivo) {
        auditRepository.save(CredencialAuditLog.builder().accion(accion).resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp(sesion.getNumeroWhatsapp()).documentoTipo(sesion.getDocumentoTipo())
                .documentoNumero(sesion.getDocumentoNumero()).jti(sesion.getJti())
                .motivo(motivo).fecha(Instant.now()).build());
    }
}
