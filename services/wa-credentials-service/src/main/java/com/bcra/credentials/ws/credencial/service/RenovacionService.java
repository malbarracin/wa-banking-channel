package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.credentials.ws.credencial.dto.EmitirCredencialResponse;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.EntregaRenovacion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.exception.RenovacionNoPreparadaException;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;
import com.bcra.credentials.ws.credencial.repository.EntregaRenovacionRepository;
import com.bcra.credentials.ws.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Prepara y entrega reemplazos; rota únicamente tras la confirmación de recepción del canal. */
@Service
@RequiredArgsConstructor
public class RenovacionService {

    private final CredencialRepository repository;
    private final CredencialAuditLogRepository auditRepository;
    private final CredencialAuditService auditService;
    private final CredencialMapper mapper;
    private final JwtEncoder jwtEncoder;
    private final TransactionTemplate transactionTemplate;
    private final EntregaRenovacionRepository entregas;
    private final JwtService jwtService;
    private final ValidacionService validacionService;

    @Value("${credencial.vigencia-minutos:1440}")
    private long vigenciaMinutos;

    @Value("${credencial.renovacion-ventana-minutos:30}")
    private long ventanaMinutos;

    /** Prepara entregas durables; la credencial en uso permanece activa hasta la confirmación de H3. */
    @Scheduled(fixedDelayString = "${credencial.renovacion.intervalo-ms:60000}")
    public void renovarProximasAExpirar() {
        if (ventanaMinutos <= 0 || vigenciaMinutos <= ventanaMinutos) {
            throw new IllegalArgumentException("La vigencia debe superar la ventana de renovación positiva");
        }
        Instant ahora = Instant.now();
        var candidatas = repository.findByEstadoAndFechaExpiracionGreaterThanAndFechaExpiracionLessThanEqual(
                CredencialEstado.ACTIVA, ahora, ahora.plus(ventanaMinutos, ChronoUnit.MINUTES));
        for (CredencialSesion candidata : candidatas) {
            try {
                transactionTemplate.executeWithoutResult(status -> repository.buscarParaRenovar(candidata.getId())
                        .ifPresent(this::renovarSiCorresponde));
            } catch (DataAccessException | JwtEncodingException exception) {
                auditService.registrar(AuditAccion.RENOVACION, AuditResultado.RECHAZADO,
                        candidata.getNumeroWhatsapp(), candidata.getDocumentoTipo(), candidata.getDocumentoNumero(),
                        "Renovación rechazada");
                throw exception;
            }
        }
    }

    private void renovarSiCorresponde(CredencialSesion anterior) {
        Instant ahora = Instant.now();
        if (anterior.getEstado() != CredencialEstado.ACTIVA || anterior.getFechaRevocacion() != null
                || !anterior.getFechaExpiracion().isAfter(ahora)
                || anterior.getFechaExpiracion().isAfter(ahora.plus(ventanaMinutos, ChronoUnit.MINUTES))) {
            return;
        }
        if (anterior.isRiesgo()) {
            registrar(anterior, AuditAccion.RENOVACION, AuditResultado.RECHAZADO, "REVERIFICACION_REQUERIDA");
            return;
        }
        if (entregas.existsById(anterior.getId())) {
            return;
        }
        Instant emision = ahora.truncatedTo(ChronoUnit.SECONDS);
        Instant expiracion = emision.plus(vigenciaMinutos, ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();
        EntregaRenovacion entrega = EntregaRenovacion.builder().anteriorId(anterior.getId()).jti(jti)
                .fechaEmision(emision).fechaExpiracion(expiracion).build();
        firmar(anterior, entrega);
        entregas.saveAndFlush(entrega);
    }

    /** Entrega repetible autenticada con el JWT en uso; no revoca ni activa credenciales. */
    public EmitirCredencialResponse obtener(String token) {
        validacionService.validar(token);
        Jwt jwt = jwtService.verificar(token);
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            CredencialSesion anterior = repository.findByJti(jwt.getId()).orElseThrow(UnauthorizedException::new);
            anterior = repository.buscarParaRenovar(anterior.getId()).orElseThrow(UnauthorizedException::new);
            exigirActiva(anterior);
            EntregaRenovacion entrega = entregas.findById(anterior.getId())
                    .orElseThrow(RenovacionNoPreparadaException::new);
            return mapper.toResponse(mapper.toRenovada(anterior, entrega.getJti(), entrega.getFechaEmision(),
                    entrega.getFechaExpiracion()), firmar(anterior, entrega));
        }));
    }

    /** Confirma posesión del reemplazo ya guardado por H3; reintentos con el mismo JWT son idempotentes. */
    public void confirmar(String token) {
        Jwt jwt = jwtService.verificar(token);
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw new UnauthorizedException();
        }
        EntregaRenovacion entrega = entregas.findByJti(jwt.getId()).orElseThrow(UnauthorizedException::new);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CredencialSesion anterior = repository.buscarParaRenovar(entrega.getAnteriorId())
                        .orElseThrow(UnauthorizedException::new);
                if (repository.findByJti(entrega.getJti()).isPresent()) {
                    validacionService.validar(token);
                    return;
                }
                exigirActiva(anterior);
                if (!anterior.getNumeroWhatsapp().equals(jwt.getSubject())
                        || !(anterior.getDocumentoTipo() + ":" + anterior.getDocumentoNumero())
                                .equals(jwt.getClaims().get("doc"))
                        || !entrega.getFechaEmision().equals(jwt.getIssuedAt())
                        || !entrega.getFechaExpiracion().equals(jwt.getExpiresAt())) {
                    throw new UnauthorizedException();
                }
                activar(anterior, entrega);
                validacionService.validar(token);
            });
        } catch (DataAccessException exception) {
            CredencialSesion anterior = repository.findById(entrega.getAnteriorId()).orElseThrow();
            auditService.registrar(AuditAccion.RENOVACION, AuditResultado.RECHAZADO,
                    anterior.getNumeroWhatsapp(), anterior.getDocumentoTipo(), anterior.getDocumentoNumero(),
                    "Renovación rechazada");
            throw exception;
        }
    }

    private void exigirActiva(CredencialSesion anterior) {
        if (anterior.getEstado() != CredencialEstado.ACTIVA || anterior.getFechaRevocacion() != null
                || anterior.isRiesgo() || !anterior.getFechaExpiracion().isAfter(Instant.now())) {
            throw new UnauthorizedException();
        }
    }

    private String firmar(CredencialSesion anterior, EntregaRenovacion entrega) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(anterior.getNumeroWhatsapp())
                .claim("doc", anterior.getDocumentoTipo() + ":" + anterior.getDocumentoNumero())
                .issuedAt(entrega.getFechaEmision()).expiresAt(entrega.getFechaExpiracion())
                .id(entrega.getJti()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private void activar(CredencialSesion anterior, EntregaRenovacion entrega) {
        CredencialSesion nueva = mapper.toRenovada(anterior, entrega.getJti(), entrega.getFechaEmision(),
                entrega.getFechaExpiracion());
        anterior.setEstado(CredencialEstado.REVOCADA);
        anterior.setFechaRevocacion(Instant.now());
        anterior.setMotivoRevocacion(MotivoRevocacion.REEMPLAZO);
        anterior.setRevocadaPor(RevocadaPor.SISTEMA);
        registrar(anterior, AuditAccion.REVOCACION, AuditResultado.EXITOSO, "REEMPLAZO");
        // El UPDATE debe preceder al INSERT para respetar la unicidad parcial de PostgreSQL.
        repository.flush();
        repository.saveAndFlush(nueva);
        registrar(nueva, AuditAccion.RENOVACION, AuditResultado.EXITOSO, "RENOVACION");
    }

    private void registrar(CredencialSesion sesion, AuditAccion accion, AuditResultado resultado, String motivo) {
        auditRepository.save(CredencialAuditLog.builder().accion(accion).resultado(resultado)
                .numeroWhatsapp(sesion.getNumeroWhatsapp()).documentoTipo(sesion.getDocumentoTipo())
                .documentoNumero(sesion.getDocumentoNumero()).jti(sesion.getJti()).motivo(motivo)
                .fecha(Instant.now()).build());
    }
}
