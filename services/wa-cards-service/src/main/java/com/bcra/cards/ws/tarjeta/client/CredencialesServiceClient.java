package com.bcra.cards.ws.tarjeta.client;

import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialResponse;

/** Validación remota de sesiones exclusivamente mediante H2, sin caché ni fallback. */
public interface CredencialesServiceClient {

    /**
     * @param credencial JWT interno de sesión; nunca debe registrarse en logs
     * @return identidad vigente confirmada por H2
     * @throws com.bcra.cards.ws.exception.UnauthorizedException si la sesión no es válida
     * @throws CredencialesServiceUnavailableException si H2 no permite completar la validación
     */
    ValidarCredencialResponse validar(String credencial);
}
