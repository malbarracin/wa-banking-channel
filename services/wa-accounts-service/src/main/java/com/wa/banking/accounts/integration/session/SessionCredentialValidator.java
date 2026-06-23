package com.wa.banking.accounts.integration.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Valida credenciales de sesión consumiendo el endpoint H2 {@code POST /api/v1/sessions/credentials/validate}.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCredentialValidator {

    private final SessionValidationClient sessionValidationClient;

    /**
     * Valida una credencial de sesión presentada por el consumidor.
     *
     * @param credentialId identificador de la credencial
     * @param token        token opaco Bearer
     * @return resultado de validación H2
     */
    public ValidateCredentialResponse validate(String credentialId, String token) {
        log.debug("Validating session credential {}", credentialId);
        return sessionValidationClient.validate(credentialId, token);
    }

    /**
     * Indica si la credencial es vigente según la respuesta de H2.
     *
     * @param credentialId identificador de la credencial
     * @param token        token opaco Bearer
     * @return {@code true} si H2 responde valid=true
     */
    public boolean isValid(String credentialId, String token) {
        ValidateCredentialResponse response = validate(credentialId, token);
        return response != null && response.isValid();
    }
}
