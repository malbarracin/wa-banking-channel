package com.bcra.credentials.ws.credencial.client;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;

/** Adaptador síncrono de H1, sin reintentos ni respuestas positivas de respaldo. */
@RequiredArgsConstructor
public class RestUsersServiceClient implements UsersServiceClient {

    private final RestClient restClient;

    @Override
    public ClienteEstadoResponse consultarEstado(String tipo, String numero) {
        if (tipo == null || tipo.isBlank() || numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("El tipo y número de documento son obligatorios");
        }
        try {
            ClienteEstadoResponse respuesta = restClient.get()
                    .uri("/api/v1/users/{tipo}/{numero}/status", tipo, numero)
                    .retrieve().body(ClienteEstadoResponse.class);
            if (respuesta == null || respuesta.estado() == null) {
                throw new UsersServiceUnavailableException();
            }
            return respuesta;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ClienteNoEncontradoException();
        } catch (RestClientException exception) {
            // La causa HTTP puede contener el documento en la URL o el cuerpo de H1.
            throw new UsersServiceUnavailableException();
        }
    }
}
