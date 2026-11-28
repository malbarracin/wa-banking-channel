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
 */
@Component
public class IdentityVerifierResolver {

    private final BankChannelProperties properties;
    private final Map<String, IdentityVerifier> verifiersByPolicy;
    private final IdentityVerifier defaultVerifier;

    public IdentityVerifierResolver(BankChannelProperties properties, List<IdentityVerifier> verifiers) {
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(verifiers, "verifiers");
        this.verifiersByPolicy = verifiers.stream()
                .collect(Collectors.toUnmodifiableMap(IdentityVerifier::policy, Function.identity()));
        this.defaultVerifier = verifiersByPolicy.get(DevContactIdentityVerifier.POLICY);
        if (defaultVerifier == null) {
            throw new IllegalStateException("Falta IdentityVerifier de desarrollo ("
                    + DevContactIdentityVerifier.POLICY + ")");
        }
    }

    /**
     * Evalúa la identidad con la política configurada.
     *
     * @param user             cliente de H1
     * @param verificationData dato aportado (no se registra)
     * @return {@code true} si coincide
     */
    public boolean verify(UserLookupResult user, String verificationData) {
        String policy = properties.getVerification().getPolicy();
        IdentityVerifier verifier = verifiersByPolicy.getOrDefault(policy, defaultVerifier);
        return verifier.matches(user, verificationData);
    }
}
