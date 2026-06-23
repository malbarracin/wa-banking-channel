package com.wa.banking.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Credencial de sesión emitida tras verificación de identidad en el canal WhatsApp.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "session_credentials")
public class SessionCredentialEntity {

    @Id
    private String id;

    @Indexed
    private String channelLinkId;

    @Indexed
    private String bankUserId;

    private String phoneNumber;

    @Builder.Default
    private CredentialStatus status = CredentialStatus.ACTIVE;

    private String tokenHash;

    private Instant issuedAt;

    private Instant expiresAt;

    private Instant revokedAt;

    private RevokeReason revokeReason;

    @Builder.Default
    private int renewalCount = 0;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
