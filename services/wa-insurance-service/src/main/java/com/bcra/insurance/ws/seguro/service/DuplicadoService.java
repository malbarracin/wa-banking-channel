package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroDuplicadoException;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

/** Impide una segunda denuncia abierta por póliza y tipo dentro de la ventana configurable. */
@Service
@Transactional(readOnly = true)
public class DuplicadoService {

    private static final Set<EstadoSiniestro> ESTADOS_ABIERTOS = EnumSet.of(
            EstadoSiniestro.RECIBIDO, EstadoSiniestro.EN_ANALISIS, EstadoSiniestro.DOCUMENTACION_PENDIENTE);

    private final SiniestroRepository siniestros;
    private final int ventanaDuplicadoDias;

    public DuplicadoService(SiniestroRepository siniestros,
            @Value("${insurance.siniestros.ventana-duplicado-dias:30}") int ventanaDuplicadoDias) {
        this.siniestros = siniestros;
        this.ventanaDuplicadoDias = ventanaDuplicadoDias;
    }

    /** @throws SiniestroDuplicadoException si hay caso abierto coincidente dentro de la ventana */
    public void asegurarSinDuplicado(UUID polizaId, TipoSiniestro tipoSiniestro) {
        Instant limite = Instant.now().minus(ventanaDuplicadoDias, ChronoUnit.DAYS);
        if (!siniestros.findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
                polizaId, tipoSiniestro, ESTADOS_ABIERTOS, limite).isEmpty()) {
            throw new SiniestroDuplicadoException();
        }
    }

    /** @return instante hasta el cual este caso participa del control de duplicados */
    public Instant ventanaHasta(Instant fechaApertura) {
        return fechaApertura.plus(ventanaDuplicadoDias, ChronoUnit.DAYS);
    }
}
