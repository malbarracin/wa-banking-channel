package com.bcra.channel.ws.vinculo.client;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Adaptador H2: validación, entrega confirmable y revocación best-effort sin participación transaccional. */
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
    public boolean validar(String credencial, String numeroWhatsapp) {
        if (credencial == null || credencial.isBlank()) {
            return false;
        }
        try {
            ValidacionResponse respuesta = restClient.post().uri("/api/v1/credenciales/validar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("credencial", credencial)).retrieve().body(ValidacionResponse.class);
            return respuesta != null && respuesta.vigente()
                    && numeroWhatsapp.equals(respuesta.numeroWhatsapp())
                    && respuesta.fechaExpiracion() != null
                    && respuesta.fechaExpiracion().isAfter(Instant.now());
        } catch (RestClientException exception) {
            return false;
        }
    }

    /** Resultado autoritativo de H2; omite datos sensibles al representarse como texto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ValidacionResponse(boolean vigente, String numeroWhatsapp, Instant fechaExpiracion) {
        @Override
        public String toString() {
            return "ValidacionResponse[vigente=" + vigente + "]";
        }
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
