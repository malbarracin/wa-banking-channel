package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import lombok.RequiredArgsConstructor;

/** Rota internamente credenciales vigentes sin repetir la verificación de identidad. */
@Service
@RequiredArgsConstructor
public class RenovacionService {

    private final CredencialRepository repository;
    private final CredencialAuditLogRepository auditRepository;
    private final CredencialAuditService auditService;
    private final CredencialMapper mapper;
    private final JwtEncoder jwtEncoder;
    private final TransactionTemplate transactionTemplate;

    @Value("${credencial.vigencia-minutos:1440}")
    private long vigenciaMinutos;

    @Value("${credencial.renovacion-ventana-minutos:30}")
    private long ventanaMinutos;

    /** Busca candidatas y confirma cada reemplazo junto con su auditoría en una transacción independiente. */
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
        Instant emision = ahora.truncatedTo(ChronoUnit.SECONDS);
        Instant expiracion = emision.plus(vigenciaMinutos, ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(anterior.getNumeroWhatsapp())
                .claim("doc", anterior.getDocumentoTipo() + ":" + anterior.getDocumentoNumero())
                .issuedAt(emision).expiresAt(expiracion).id(jti).build();
        jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));

        CredencialSesion nueva = mapper.toRenovada(anterior, jti, emision, expiracion);
        anterior.setEstado(CredencialEstado.REVOCADA);
        anterior.setFechaRevocacion(ahora);
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
