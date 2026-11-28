package com.bcra.channel.ws.binding.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.binding.entity.VerificationAttempt;

/**
 * Persistencia de intentos de verificación de identidad.
 */
public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, UUID> {

    /**
     * Cuenta intentos fallidos de un vínculo desde un instante inclusive.
     *
     * @param bindingId identificador del vínculo
     * @param since     inicio de la ventana (inclusive)
     * @return cantidad de fallos en la ventana
     */
    long countByBindingIdAndSuccessFalseAndAttemptedAtGreaterThanEqual(UUID bindingId, Instant since);
}
