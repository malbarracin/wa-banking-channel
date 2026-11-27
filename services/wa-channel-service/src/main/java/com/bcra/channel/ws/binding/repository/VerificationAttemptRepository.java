package com.bcra.channel.ws.binding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.binding.entity.VerificationAttempt;

/**
 * Persistencia de intentos de verificación de identidad.
 */
public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, UUID> {
}
