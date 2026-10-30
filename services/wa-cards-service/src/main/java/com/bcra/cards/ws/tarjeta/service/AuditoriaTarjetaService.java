package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaAccion;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaResultado;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaTarjeta;
import com.bcra.cards.ws.tarjeta.repository.AuditoriaTarjetaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persiste la auditoría en una transacción independiente de la operación. */
@Service
@RequiredArgsConstructor
public class AuditoriaTarjetaService {

    private final AuditoriaTarjetaRepository auditoriaRepository;

    /** Registra el bloqueo autorizado sin persistir la credencial de sesión. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarBloqueoTemporal(ValidarCredencialResponse identidad, UUID tarjetaId) {
        var auditoria = new AuditoriaTarjeta();
        auditoria.setAccion(AuditoriaAccion.BLOQUEAR_TEMPORAL);
        auditoria.setResultado(AuditoriaResultado.EXITOSO);
        auditoria.setNumeroWhatsapp(identidad.numeroWhatsapp());
        auditoria.setDocumentoTipo(identidad.documentoTipo());
        auditoria.setDocumentoNumero(identidad.documentoNumero());
        auditoria.setTarjetaId(tarjetaId);
        auditoria.setFecha(Instant.now());
        auditoriaRepository.save(auditoria);
    }

    /** Registra un listado autorizado sin guardar la credencial ni datos de tarjeta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarListado(ValidarCredencialResponse identidad) {
        var auditoria = new AuditoriaTarjeta();
        auditoria.setAccion(AuditoriaAccion.LISTAR_TARJETAS);
        auditoria.setResultado(AuditoriaResultado.EXITOSO);
        auditoria.setNumeroWhatsapp(identidad.numeroWhatsapp());
        auditoria.setDocumentoTipo(identidad.documentoTipo());
        auditoria.setDocumentoNumero(identidad.documentoNumero());
        auditoria.setFecha(Instant.now());
        auditoriaRepository.save(auditoria);
    }
}
