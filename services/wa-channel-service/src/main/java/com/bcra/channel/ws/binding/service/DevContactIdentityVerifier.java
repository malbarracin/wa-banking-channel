package com.bcra.channel.ws.binding.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.bcra.channel.ws.client.UserLookupResult;

/**
 * Implementación de desarrollo: compara {@code verificationData} con teléfono o email públicos de H1.
 * No es la política definitiva del banco; sirve hasta que se configure una real.
 */
@Component
public class DevContactIdentityVerifier implements IdentityVerifier {

    public static final String POLICY = "document-code";

    @Override
    public String policy() {
        return POLICY;
    }

    @Override
    public boolean matches(UserLookupResult user, String verificationData) {
        Objects.requireNonNull(user, "user");
        if (verificationData == null || verificationData.isBlank()) {
            return false;
        }
        String candidate = verificationData.trim();
        if (candidate.equals(nullToEmpty(user.contactPhone()).trim())) {
            return true;
        }
        String email = nullToEmpty(user.contactEmail()).trim();
        return !email.isEmpty() && email.equalsIgnoreCase(candidate);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "DevContactIdentityVerifier{policy=" + POLICY + '}';
    }
}
