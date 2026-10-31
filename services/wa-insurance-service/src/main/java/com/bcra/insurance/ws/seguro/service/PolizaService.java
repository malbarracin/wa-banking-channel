package com.bcra.insurance.ws.seguro.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.insurance.ws.seguro.client.CredencialesServiceClient;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;
import com.bcra.insurance.ws.seguro.dto.PolizaResponse;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.exception.PolizaNoAutorizadaException;
import com.bcra.insurance.ws.seguro.exception.PolizaNoEncontradaException;
import com.bcra.insurance.ws.seguro.mapper.PolizaMapper;
import com.bcra.insurance.ws.seguro.repository.PolizaRepository;

import lombok.RequiredArgsConstructor;

/** Consultas limitadas al titular o asegurado autenticado por H2. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PolizaService {

    private final CredencialesServiceClient credenciales;
    private final PolizaRepository polizas;
    private final PolizaMapper mapper;

    /** @return pólizas autorizadas vigentes en la fecha actual, o una lista vacía */
    public List<PolizaResponse> listar(String credencial) {
        ValidarCredencialResponse identidad = credenciales.validar(credencial);
        return polizas.buscarVigentesAutorizadas(identidad.documentoTipo(), identidad.documentoNumero(),
                LocalDate.now()).stream().map(mapper::toResponse).toList();
    }

    /** @return póliza existente después de validar la sesión y su relación con el cliente */
    public Poliza obtenerAutorizada(String credencial, UUID polizaId) {
        ValidarCredencialResponse identidad = credenciales.validar(credencial);
        Poliza poliza = polizas.findById(polizaId).orElseThrow(PolizaNoEncontradaException::new);
        boolean titular = identidad.documentoTipo().equals(poliza.getTitularDocumentoTipo())
                && identidad.documentoNumero().equals(poliza.getTitularDocumentoNumero());
        boolean asegurado = identidad.documentoTipo().equals(poliza.getAseguradoDocumentoTipo())
                && identidad.documentoNumero().equals(poliza.getAseguradoDocumentoNumero());
        if (!titular && !asegurado) {
            throw new PolizaNoAutorizadaException();
        }
        return poliza;
    }
}
