package com.bcra.insurance.ws.seguro.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.dto.SiniestroResponse;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoAutorizadoException;
import com.bcra.insurance.ws.seguro.exception.SiniestroNoEncontradoException;
import com.bcra.insurance.ws.seguro.mapper.SiniestroMapper;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;
import com.bcra.insurance.ws.seguro.repository.SiniestroRepository;

import lombok.RequiredArgsConstructor;

/** Seguimiento de casos propios del titular; el estado proviene del sistema de siniestros. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SiniestroService {

    private final CredencialesServiceClient credenciales;
    private final SiniestroRepository siniestros;
    private final PolizaRepository polizas;
    private final SiniestroMapper mapper;

    /**
     * Consulta el estado del caso para el titular autenticado.
     *
     * @param credencial JWT de sesión en {@code X-Credencial-Sesion}
     * @param numeroCaso identificador del caso
     * @return estado y acción pendiente informados por el sistema
     */
    public SiniestroResponse consultar(String credencial, String numeroCaso) {
        return mapper.toResponse(obtenerPropio(credencial, numeroCaso));
    }

    /**
     * Valida la sesión, localiza el caso y exige titularidad de la póliza asociada.
     *
     * @return caso propio del titular autenticado
     */
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
}
