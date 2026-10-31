package com.bcra.channel.ws.vinculo.client;

import java.time.Instant;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Adaptador H2: entrega confirmable y revocación best-effort sin participación transaccional. */
@RequiredArgsConstructor
@Slf4j
public class RestCredencialesServiceClient implements CredencialesServiceClient {

    private final RestClient restClient;

    @Override
    public RenovacionCredencialResponse obtenerRenovacion(String credencialActual) {
        RenovacionCredencialResponse respuesta = restClient.post().uri("/api/v1/credenciales/renovacion/obtener")
                .body(new SolicitudRenovacionRequest(credencialActual)).retrieve()
                .body(RenovacionCredencialResponse.class);
        if (respuesta == null || respuesta.credencial() == null || respuesta.credencial().isBlank()
                || respuesta.fechaExpiracion() == null || !respuesta.fechaExpiracion().isAfter(Instant.now())) {
            throw new RestClientException("Respuesta de renovación inválida");
        }
        return respuesta;
    }

    @Override
    public void confirmarRenovacion(String credencialNueva) {
        restClient.post().uri("/api/v1/credenciales/renovacion/confirmar")
                .body(new SolicitudRenovacionRequest(credencialNueva)).retrieve().toBodilessEntity();
    }

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
