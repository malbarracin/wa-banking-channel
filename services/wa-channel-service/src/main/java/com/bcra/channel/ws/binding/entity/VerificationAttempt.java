package com.bcra.channel.ws.binding.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intento de verificación de identidad asociado a un vínculo.
 */
@Entity
@Table(name = "verification_attempts")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VerificationAttempt {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "binding_id", nullable = false)
    private UUID bindingId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(nullable = false)
    private boolean success;

    @Override
    public String toString() {
        return "VerificationAttempt{id=" + id
                + ", bindingId=" + bindingId
                + ", attemptedAt=" + attemptedAt
                + ", success=" + success
                + '}';
    }
}
