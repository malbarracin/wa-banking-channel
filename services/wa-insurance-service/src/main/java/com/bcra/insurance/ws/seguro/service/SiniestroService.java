package com.bcra.insurance.ws.seguro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.dto.DenunciaRequest;
import com.bcra.insurance.ws.seguro.dto.DenunciaResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoAutorizadoException;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoEncontradoException;
import com.bcra.insurance.ws.seguro.mapper.SiniestroMapper;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Apertura de denuncias confirmadas y seguimiento de casos propios del titular.
 * El estado de seguimiento proviene del sistema de siniestros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiniestroService {

    private static final String PROXIMOS_PASOS =
            "Enviá la documentación requerida por los canales oficiales del banco.";
    private static final String PLAZOS =
            "Los plazos dependen del proceso formal correspondiente a tu ramo; no prometemos liquidación por este canal.";

    private final PolizaService polizaService;
    private final DuplicadoService duplicados;
    private final CredencialesServiceClient credenciales;
    private final SiniestroRepository siniestros;
    private final PolizaRepository polizas;
    private final SiniestroMapper mapper;

    /**
     * Valida la sesión y la póliza, rechaza duplicados abiertos y asigna un número de caso único.
     *
     * @return confirmación con número de caso y próximos pasos
     */
    @Transactional
    public DenunciaResponse denunciar(String credencial, DenunciaRequest request) {
        Poliza poliza = polizaService.obtenerAutorizada(credencial, request.polizaId());
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

    /**
     * Consulta el estado del caso para el titular autenticado.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return estado y acción pendiente informados por el sistema
     */
    @Transactional(readOnly = true)
    public SiniestroResponse consultar(String credencial, String numeroCaso) {
        return mapper.toResponse(obtenerPropio(credencial, numeroCaso));
    }

    /**
     * Valida la sesión, localiza el caso y exige titularidad de la póliza asociada.
     *
     * @return caso propio del titular autenticado
     */
    @Transactional(readOnly = true)
    public Siniestro obtenerPropio(String credencial, String numeroCaso) {
        ValidarCredencialResponse identidad = credenciales.validar(credencial);
        Siniestro siniestro = siniestros.findByNumeroCaso(numeroCaso)
                .orElseThrow(SiniestroNoEncontradoException::new);
        Poliza poliza = polizas.findById(siniestro.getPolizaId())
                .orElseThrow(SiniestroNoEncontradoException::new);
        boolean titular = identidad.documentoTipo().equals(poliza.getTitularDocumentoTipo())
                && identidad.documentoNumero().equals(poliza.getTitularDocumentoNumero());
        if (!titular) {
            throw new SiniestroNoAutorizadoException();
        }
        return siniestro;
    }

    private static String nuevoNumeroCaso() {
        return "SIN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
