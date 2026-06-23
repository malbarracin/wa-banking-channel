package com.wa.banking.channel.entity;

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
 * Entidad principal del vínculo entre un número WhatsApp y un cliente bancario.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "whatsapp_links")
public class WhatsAppLinkEntity {

    @Id
    private String id;

    @Indexed
    private String phoneNumber;

    @Indexed
    private String bankUserId;

    @Builder.Default
    private LinkStatus status = LinkStatus.NO_LINK;

    private Instant termsAcceptedAt;

    @Builder.Default
    private int verificationAttempts = 0;

    private Instant verificationBlockedUntil;

    private DocumentType documentType;

    private String documentNumber;

    @Builder.Default
    private boolean identityVerified = false;

    private String sessionCredentialId;

    @Builder.Default
    private ChannelPreferences preferences = ChannelPreferences.builder().build();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
