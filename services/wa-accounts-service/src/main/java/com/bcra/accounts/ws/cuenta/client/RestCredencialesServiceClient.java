package com.bcra.accounts.ws.cuenta.client;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialRequest;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Adaptador HTTP de validación; cada invocación consulta H2. */
@RequiredArgsConstructor
public class RestCredencialesServiceClient implements CredencialesServiceClient {

    private static final String VALIDAR_PATH = "/api/v1/credenciales/validar";

    private final RestClient restClient;

    @Override
    public ValidarCredencialResponse validar(String credencial) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        try {
            ValidarCredencialResponse respuesta = restClient.post().uri(VALIDAR_PATH)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(new ValidarCredencialRequest(credencial))
                    .retrieve()
                    .onStatus(estado -> estado.value() == HttpStatus.UNAUTHORIZED.value(), (request, response) -> {
                        throw new UnauthorizedException("Credencial no válida");
                    })
                    .onStatus(estado -> !estado.is2xxSuccessful(), (request, response) -> {
                        throw new CredencialesServiceUnavailableException();
                    })
                    .body(ValidarCredencialResponse.class);
            if (respuesta == null) {
                throw new CredencialesServiceUnavailableException();
            }
            if (!respuesta.vigente()) {
                throw new UnauthorizedException("Credencial no válida");
            }
            if (esVacio(respuesta.numeroWhatsapp()) || esVacio(respuesta.documentoTipo())
                    || esVacio(respuesta.documentoNumero()) || respuesta.fechaExpiracion() == null) {
                throw new CredencialesServiceUnavailableException();
            }
            if (!respuesta.fechaExpiracion().isAfter(Instant.now())) {
                throw new UnauthorizedException("Credencial no válida");
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
