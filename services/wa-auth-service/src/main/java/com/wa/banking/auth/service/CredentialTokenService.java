package com.wa.banking.auth.service;

import com.wa.banking.auth.integration.SessionCredentialProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Generación de tokens opacos y hash SHA-256 con pepper para almacenamiento seguro.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Service
@RequiredArgsConstructor
public class CredentialTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SessionCredentialProperties properties;

    /**
     * Genera un token opaco aleatorio para la credencial de sesión.
     *
     * @return token en claro (solo para respuesta de emisión)
     */
    public String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "sess_" + UUID.randomUUID() + "_" + encoded;
    }

    /**
     * Calcula el hash SHA-256 del token con pepper configurado.
     *
     * @param token token en claro
     * @return hash hexadecimal
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = token + properties.getPepper();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    /**
     * Compara un token en claro con el hash almacenado.
     *
     * @param token      token presentado
     * @param storedHash hash persistido
     * @return true si coinciden
     */
    public boolean matches(String token, String storedHash) {
        if (token == null || storedHash == null) {
            return false;
        }
        return storedHash.equals(hashToken(token));
    }
}
