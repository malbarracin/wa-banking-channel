package com.bcra.insurance.ws.seguro.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.dto.DocumentacionPendienteResponse;
import com.bcra.insurance.ws.seguro.entity.Siniestro;

import lombok.RequiredArgsConstructor;

/** Recordatorio de documentación pendiente con orientación a canales oficiales. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentacionService {

    private static final String ORIENTACION_ENVIO =
            "Enviá estos documentos por los canales oficiales del banco; este chat no reemplaza el proceso formal.";

    private final SiniestroService siniestros;

    /**
     * Lista simple de documentos pendientes del caso propio.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return documentos informados por el sistema y orientación de envío
     */
    public DocumentacionPendienteResponse listarPendiente(String credencial, String numeroCaso) {
        Siniestro siniestro = siniestros.obtenerPropio(credencial, numeroCaso);
        List<String> documentos = siniestro.getDocumentosPendientes() == null
                ? List.of()
                : List.copyOf(siniestro.getDocumentosPendientes());
        return new DocumentacionPendienteResponse(documentos, ORIENTACION_ENVIO);
    }
}
