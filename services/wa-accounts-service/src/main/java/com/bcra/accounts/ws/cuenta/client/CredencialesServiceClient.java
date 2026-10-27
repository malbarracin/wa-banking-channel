package com.bcra.accounts.ws.cuenta.client;

import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;

/** Validación remota de sesiones exclusivamente mediante H2, sin caché ni fallback. */
public interface CredencialesServiceClient {

    /**
     * @param credencial JWT interno de sesión; nunca debe registrarse en logs
     * @return identidad vigente confirmada por H2
     * @throws com.bcra.accounts.ws.exception.UnauthorizedException si la sesión no es válida
     * @throws CredencialesServiceUnavailableException si H2 no permite completar la validación
     */
    ValidarCredencialResponse validar(String credencial);
}
