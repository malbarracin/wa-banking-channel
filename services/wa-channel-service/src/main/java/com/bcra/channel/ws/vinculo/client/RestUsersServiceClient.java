package com.bcra.channel.ws.vinculo.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;

/** Implementación síncrona de las consultas de cliente en H1. */
@RequiredArgsConstructor
public class RestUsersServiceClient implements UsersServiceClient {

    private final RestClient restClient;

    @Override
    public ClienteEstadoResponse consultarEstado(String tipo, String numero) {
        ClienteEstadoResponse respuesta = restClient.get()
                .uri("/api/v1/users/{tipo}/{numero}/status", tipo, numero)
                .retrieve().body(ClienteEstadoResponse.class);
        if (respuesta == null || respuesta.estado() == null) {
            throw new RestClientException("H1 devolvió un estado vacío");
        }
        return respuesta;
    }

    @Override
    public ClientePerfilResponse consultarPerfil(String tipo, String numero) {
        ClientePerfilResponse respuesta = restClient.get()
                .uri("/api/v1/users/{tipo}/{numero}", tipo, numero)
                .retrieve().body(ClientePerfilResponse.class);
        if (respuesta == null) {
            throw new RestClientException("H1 devolvió un perfil vacío");
        }
        return respuesta;
    }
}
