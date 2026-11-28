package com.bcra.channel.ws.binding.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.config.BankChannelProperties;

/**
 * Elige la estrategia {@link IdentityVerifier} según {@code bank.channel.verification.policy}.
 * Falla al arrancar si la política configurada no tiene implementación.
 */
@Component
public class IdentityVerifierResolver {

    private final IdentityVerifier verifier;

    public IdentityVerifierResolver(BankChannelProperties properties, List<IdentityVerifier> verifiers) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(verifiers, "verifiers");
        Map<String, IdentityVerifier> verifiersByPolicy = verifiers.stream()
                .collect(Collectors.toUnmodifiableMap(IdentityVerifier::policy, Function.identity()));
        String policy = properties.getVerification().getPolicy();
        IdentityVerifier resolved = verifiersByPolicy.get(policy);
        if (resolved == null) {
            throw new IllegalStateException(
                    "Política de verificación desconocida: '" + policy + "'. Registradas: "
                            + verifiersByPolicy.keySet());
        }
        this.verifier = resolved;
    }

    /**
     * Evalúa la identidad con la política configurada.
     *
     * @param user             cliente de H1
     * @param verificationData dato aportado (no se registra)
     * @return {@code true} si coincide
     */
    public boolean verify(UserLookupResult user, String verificationData) {
        return verifier.matches(user, verificationData);
    }
}
