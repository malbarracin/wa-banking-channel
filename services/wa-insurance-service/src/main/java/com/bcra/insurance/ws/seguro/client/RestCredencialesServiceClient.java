package com.bcra.insurance.ws.seguro.client;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bcra.insurance.ws.exception.UnauthorizedException;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialRequest;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialResponse;

import lombok.RequiredArgsConstructor;

/** Adaptador HTTP de validación; cada invocación consulta H2. */
@RequiredArgsConstructor
public class RestCredencialesServiceClient implements CredencialesServiceClient {

    private static final String VALIDAR_PATH = "/api/v1/credenciales/validar";
    private static final String CREDENCIAL_INVALIDA = "Verificá tu identidad para consultar tus seguros.";

    private final RestClient restClient;

    @Override
    public ValidarCredencialResponse validar(String credencial) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException(CREDENCIAL_INVALIDA);
        }
        try {
            ValidarCredencialResponse respuesta = restClient.post().uri(VALIDAR_PATH)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(new ValidarCredencialRequest(credencial))
                    .retrieve()
                    .onStatus(estado -> estado.value() == HttpStatus.UNAUTHORIZED.value(), (request, response) -> {
                        throw new UnauthorizedException(CREDENCIAL_INVALIDA);
                    })
                    .onStatus(estado -> !estado.is2xxSuccessful(), (request, response) -> {
                        throw new CredencialesServiceUnavailableException();
                    })
                    .body(ValidarCredencialResponse.class);
            if (respuesta == null) {
                throw new CredencialesServiceUnavailableException();
            }
            if (!respuesta.vigente()) {
                throw new UnauthorizedException(CREDENCIAL_INVALIDA);
            }
            if (esVacio(respuesta.numeroWhatsapp()) || esVacio(respuesta.documentoTipo())
                    || esVacio(respuesta.documentoNumero()) || respuesta.fechaExpiracion() == null) {
                throw new CredencialesServiceUnavailableException();
            }
            if (!respuesta.fechaExpiracion().isAfter(Instant.now())) {
                throw new UnauthorizedException(CREDENCIAL_INVALIDA);
            }
            return respuesta;
        } catch (RestClientException exception) {
            throw new CredencialesServiceUnavailableException(exception);
        }
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
