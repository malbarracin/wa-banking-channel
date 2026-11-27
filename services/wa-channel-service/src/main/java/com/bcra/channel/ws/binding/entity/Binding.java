package com.bcra.channel.ws.binding.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vínculo entre un número WhatsApp y un cliente del banco.
 * {@code toString} omite el número para no filtrar PII.
 */
@Entity
@Table(name = "bindings")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Binding {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "whatsapp_number", nullable = false, length = 32, unique = true)
    private String whatsappNumber;

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BindingStatus status;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "language_preference", nullable = false, length = 16)
    private String languagePreference = "es";

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "Binding{id=" + id
                + ", clientId=" + clientId
                + ", status=" + status
                + ", languagePreference=" + languagePreference
                + ", notificationsEnabled=" + notificationsEnabled
                + ", actor=" + actor
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}
