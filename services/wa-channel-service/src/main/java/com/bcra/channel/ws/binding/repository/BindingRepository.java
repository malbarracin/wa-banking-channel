package com.bcra.channel.ws.binding.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;

/**
 * Persistencia de vínculos WhatsApp ↔ cliente.
 */
public interface BindingRepository extends JpaRepository<Binding, UUID> {

    Optional<Binding> findByWhatsappNumber(String whatsappNumber);

    /**
     * Lectura con bloqueo pesimista para cambios de estado concurrentes.
     *
     * @param whatsappNumber número en E.164
     * @return vínculo si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Binding b WHERE b.whatsappNumber = :whatsappNumber")
    Optional<Binding> findByWhatsappNumberForUpdate(@Param("whatsappNumber") String whatsappNumber);

    /**
     * Anti-fraude: ¿hay vínculo ACTIVO del número con un clientId distinto?
     *
     * @param whatsappNumber número en E.164
     * @param status         estado buscado (típicamente {@link BindingStatus#ACTIVO})
     * @param clientId       clientId que se intenta vincular
     * @return {@code true} si existe conflicto
     */
    boolean existsByWhatsappNumberAndStatusAndClientIdNot(
            String whatsappNumber, BindingStatus status, UUID clientId);
}
