package com.wa.banking.accounts.integration.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Respuesta de validación de credencial recibida del servicio H2.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateCredentialResponse {

    private boolean valid;

    private String bankUserId;

    private String channelLinkId;

    private Instant expiresAt;
}
