package com.bank.users.user.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bank.users.user.dto.AuditEventType;
import com.bank.users.user.dto.AuditRejectionReason;
import com.bank.users.user.dto.AuditResult;
import com.bank.users.user.entity.AuditEvent;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.repository.AuditEventRepository;

/**
 * Escritura y lectura de la auditoría del ciclo de vida del usuario, aislada de los flujos HTTP.
 *
 * <p>Los eventos exitosos ({@code ALTA}, {@code CAMBIO_ESTADO}, {@code MODIFICACION_DATOS}) se unen a la
 * transacción del caso de uso: si la operación revierte, su evento también. {@code ALTA_RECHAZADA} se
 * escribe en una transacción propia ({@link Propagation#REQUIRES_NEW}) para que sobreviva al rollback
 * del alta rechazada. Nunca se registran valores de datos personales: solo id interno y metadatos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventService {

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final String FIELD_SEPARATOR = ",";

    private final AuditEventRepository repository;

    /**
     * Registra el alta exitosa de un usuario.
     *
     * @param userId identificador interno del usuario creado
     * @return evento persistido
     */
    @Transactional
    public AuditEvent recordUserCreated(Long userId) {
        return save(AuditEvent.builder()
                .userId(requireUserId(userId))
                .eventType(AuditEventType.ALTA)
                .result(AuditResult.OK)
                .build());
    }

    /**
     * Registra un cambio de estado exitoso.
     *
     * @param userId         identificador interno del usuario
     * @param previousStatus estado anterior
     * @param newStatus      estado nuevo
     * @return evento persistido
     */
    @Transactional
    public AuditEvent recordStatusChanged(Long userId, UserStatus previousStatus, UserStatus newStatus) {
        return save(AuditEvent.builder()
                .userId(requireUserId(userId))
                .eventType(AuditEventType.CAMBIO_ESTADO)
                .previousStatus(Objects.requireNonNull(previousStatus, "previousStatus"))
                .newStatus(Objects.requireNonNull(newStatus, "newStatus"))
                .result(AuditResult.OK)
                .build());
    }

    /**
     * Registra una modificación de datos permitidos, guardando solo los nombres de los campos.
     *
     * @param userId         identificador interno del usuario
     * @param modifiedFields nombres de los campos modificados (identificadores, no valores)
     * @return evento persistido
     * @throws IllegalArgumentException si no hay campos o alguno no es un nombre de campo válido
     */
    @Transactional
    public AuditEvent recordDataModified(Long userId, Collection<String> modifiedFields) {
        return save(AuditEvent.builder()
                .userId(requireUserId(userId))
                .eventType(AuditEventType.MODIFICACION_DATOS)
                .modifiedFields(toFieldList(modifiedFields))
                .result(AuditResult.OK)
                .build());
    }

    /**
     * Registra un alta rechazada en una transacción independiente, de modo que el evento persiste
     * aunque la transacción del alta revierta. No referencia a ningún usuario.
     *
     * @param reason motivo codificado del rechazo
     * @return evento persistido
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordCreationRejected(AuditRejectionReason reason) {
        return save(AuditEvent.builder()
                .eventType(AuditEventType.ALTA_RECHAZADA)
                .reason(Objects.requireNonNull(reason, "reason"))
                .result(AuditResult.REJECTED)
                .build());
    }

    /**
     * Devuelve los eventos de un usuario, del más reciente al más antiguo. Uso interno (sin endpoint).
     *
     * @param userId identificador interno del usuario
     * @return eventos ordenados por {@code occurredAt} descendente; lista vacía si no hay
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByUser(Long userId) {
        return repository.findByUserIdOrderByOccurredAtDesc(requireUserId(userId));
    }

    private AuditEvent save(AuditEvent event) {
        AuditEvent saved = repository.save(event);
        log.info("Audit event recorded: type={} result={} userId={}",
                saved.getEventType(), saved.getResult(), saved.getUserId());
        return saved;
    }

    private static Long requireUserId(Long userId) {
        return Objects.requireNonNull(userId, "userId");
    }

    private static String toFieldList(Collection<String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("modifiedFields must not be empty");
        }
        TreeSet<String> names = new TreeSet<>();
        for (String field : fields) {
            if (field == null || !FIELD_NAME.matcher(field).matches()) {
                throw new IllegalArgumentException("modifiedFields must contain field names only");
            }
            names.add(field);
        }
        return String.join(FIELD_SEPARATOR, names);
    }
}
