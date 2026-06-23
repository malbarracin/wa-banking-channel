package com.wa.banking.accounts.integration.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de validación de credencial enviada al servicio H2.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateCredentialRequest {

    private String credentialId;

    private String token;
}
