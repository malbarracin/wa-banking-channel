package com.bcra.insurance.ws.seguro.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.dto.CoberturaResponse;
import com.bcra.insurance.ws.seguro.entity.Cobertura;
import com.bcra.insurance.ws.seguro.entity.Exclusion;
import com.bcra.insurance.ws.seguro.repository.CoberturaRepository;
import com.bcra.insurance.ws.seguro.repository.ExclusionRepository;

import lombok.RequiredArgsConstructor;

/** Consulta los textos resumidos del registro, sin exponer la letra completa de la póliza. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CoberturaService {

    private static final String NOTA = "Este es un resumen informativo. En caso de disputa prevalecen "
            + "las condiciones generales oficiales de la póliza.";

    private final PolizaService polizas;
    private final CoberturaRepository coberturas;
    private final ExclusionRepository exclusiones;

    /** @return coberturas principales y exclusiones relevantes de una póliza autorizada */
    public CoberturaResponse consultar(String credencial, UUID polizaId) {
        polizas.obtenerAutorizada(credencial, polizaId);
        return new CoberturaResponse(coberturas.findByPolizaId(polizaId).stream()
                .map(Cobertura::getResumen).toList(), exclusiones.findByPolizaId(polizaId).stream()
                .map(Exclusion::getDescripcion).toList(), true, NOTA);
    }
}
