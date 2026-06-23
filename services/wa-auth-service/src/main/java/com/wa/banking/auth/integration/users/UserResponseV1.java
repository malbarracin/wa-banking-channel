package com.wa.banking.auth.integration.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO de respuesta del servicio H1 de usuarios bancarios.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserResponseV1 {

    private String id;

    private String documentType;

    private String documentNumber;

    private String displayName;

    private String email;

    private String phone;

    private Map<String, Object> preferences;

    private String status;

    private boolean canLinkChannel;

    private Instant createdAt;

    private Instant updatedAt;
}
