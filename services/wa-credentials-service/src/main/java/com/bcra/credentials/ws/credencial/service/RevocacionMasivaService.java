package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.bcra.credentials.ws.credencial.dto.RevocacionMasivaRequest;
import com.bcra.credentials.ws.credencial.dto.RevocacionMasivaResponse;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;

import lombok.RequiredArgsConstructor;

/** Aplica decisiones masivas del banco y confirma cambios y auditorías juntos. */
@Service
@Validated
@RequiredArgsConstructor
public class RevocacionMasivaService {

    private final CredencialRepository repository;
    private final CredencialAuditLogRepository auditRepository;

    /** @return cantidad modificada; repetir un alcance sin nuevas credenciales activas devuelve cero. */
    @Transactional
    public RevocacionMasivaResponse revocar(@NotNull @Valid RevocacionMasivaRequest request) {
        List<CredencialSesion> credenciales = "TODAS".equals(request.criterio())
                ? repository.buscarPorEstadoParaRevocar(CredencialEstado.ACTIVA)
                : repository.buscarPorClienteParaRevocar(CredencialEstado.ACTIVA,
                        request.segmento().documentoTipo(), request.segmento().documentoNumero());
        Instant ahora = Instant.now();
        for (CredencialSesion credencial : credenciales) {
            credencial.setEstado(CredencialEstado.REVOCADA);
            credencial.setFechaRevocacion(ahora);
            credencial.setMotivoRevocacion(MotivoRevocacion.REVOCACION_MASIVA);
            credencial.setRevocadaPor(RevocadaPor.BANCO);
            auditRepository.save(CredencialAuditLog.builder()
                    .accion(AuditAccion.REVOCACION).resultado(AuditResultado.EXITOSO)
                    .numeroWhatsapp(credencial.getNumeroWhatsapp())
                    .documentoTipo(credencial.getDocumentoTipo()).documentoNumero(credencial.getDocumentoNumero())
                    .jti(credencial.getJti()).motivo(MotivoRevocacion.REVOCACION_MASIVA.name()).fecha(ahora).build());
        }
        return new RevocacionMasivaResponse(credenciales.size());
    }
}
