package com.bcra.channel.ws.vinculo.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Revocación best-effort sin reintentos automáticos ni participación transaccional. */
@RequiredArgsConstructor
@Slf4j
public class RestCredencialesServiceClient implements CredencialesServiceClient {

    private final RestClient restClient;

    @Override
    public boolean solicitarRevocacion(String numeroWhatsapp, String motivo) {
        try {
            restClient.post().uri("/api/v1/credenciales/{numero}/revocacion", numeroWhatsapp)
                    .body(new SolicitudRevocacionRequest(motivo)).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            // El mensaje y stack de HTTP pueden contener URL, teléfono o cuerpo sensible de H2.
            log.warn("No se confirmó la solicitud de revocación a H2; tipo de fallo: {}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
