package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.mapper.SiniestroMapper;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Apertura de denuncias confirmadas con número de caso único y control de duplicados. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiniestroService {

    private static final String PROXIMOS_PASOS =
            "Enviá la documentación requerida por los canales oficiales del banco.";
    private static final String PLAZOS =
            "Los plazos dependen del proceso formal correspondiente a tu ramo; no prometemos liquidación por este canal.";

    private final PolizaService polizas;
    private final DuplicadoService duplicados;
    private final SiniestroRepository siniestros;
    private final SiniestroMapper mapper;

    /**
     * Valida la sesión y la póliza, rechaza duplicados abiertos y asigna un número de caso único.
     *
     * @return confirmación con número de caso y próximos pasos
     */
    @Transactional
    public DenunciaResponse denunciar(String credencial, DenunciaRequest request) {
        Poliza poliza = polizas.obtenerAutorizada(credencial, request.polizaId());
        duplicados.asegurarSinDuplicado(poliza.getId(), request.tipoSiniestro());

        Instant apertura = Instant.now();
        Siniestro abierto = siniestros.save(Siniestro.builder()
                .numeroCaso(nuevoNumeroCaso())
                .polizaId(poliza.getId())
                .tipoSiniestro(request.tipoSiniestro())
                .fechaSiniestro(request.fechaSiniestro())
                .descripcion(request.descripcion())
                .estado(EstadoSiniestro.RECIBIDO)
                .fechaApertura(apertura)
                .ventanaDuplicadoHasta(duplicados.ventanaHasta(apertura))
                .build());

        log.info("Siniestro abierto con número de caso asignado");
        return mapper.toDenunciaResponse(abierto, PROXIMOS_PASOS, PLAZOS);
    }

    private static String nuevoNumeroCaso() {
        return "SIN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
