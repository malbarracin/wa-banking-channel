package com.bcra.channel.ws.binding.service;

import com.bcra.channel.ws.client.UserLookupResult;

/**
 * Estrategia de verificación de identidad según la política del banco.
 */
public interface IdentityVerifier {

    /**
     * Nombre de política que implementa (coincide con {@code bank.channel.verification.policy}).
     *
     * @return clave de política
     */
    String policy();

    /**
     * Compara el dato aportado con los datos públicos del cliente en H1.
     * No debe registrar ni devolver el contenido de {@code verificationData}.
     *
     * @param user             cliente encontrado en H1
     * @param verificationData dato aportado por el canal
     * @return {@code true} si la identidad se considera verificada
     */
    boolean matches(UserLookupResult user, String verificationData);
}
