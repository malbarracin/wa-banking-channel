package com.bank.users.user.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.users.exception.UserAlreadyExistsException;
import com.bank.users.exception.UserNotFoundException;
import com.bank.users.user.dto.AuditRejectionReason;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.EligibilityResponse;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Casos de uso del ciclo de vida de usuarios.
 *
 * <p>El alta crea clientes {@code ACTIVE} con documento (tipo + número) único en cualquier estado y deja
 * evidencia de auditoría tanto del alta como del rechazo por duplicado.</p>
 *
 * <p>Las consultas son de solo lectura: no modifican datos ni estado, devuelven la misma proyección
 * para todos los actores H1 y no exponen auditoría, historial ni datos de canal.</p>
 *
 * <p>La actualización solo modifica los datos permitidos ({@code displayName}, {@code email}) y registra
 * {@code MODIFICACION_DATOS} con los nombres de los campos efectivamente cambiados.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_EMAIL = "email";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditEventService auditEventService;

    /**
     * Da de alta un cliente en estado {@code ACTIVE}.
     *
     * <p>El documento (tipo + número) debe ser único en cualquier estado, incluida la baja lógica. La
     * restricción única de la base de datos resuelve las altas concurrentes: la colisión se traduce a
     * {@link UserAlreadyExistsException}, nunca a un error interno. El alta exitosa registra {@code ALTA}
     * en la misma transacción; el rechazo registra {@code ALTA_RECHAZADA} en una transacción propia, que
     * persiste aunque el alta se revierta.</p>
     *
     * @param request datos de alta ya validados
     * @return proyección pública del usuario creado
     * @throws UserAlreadyExistsException si el documento ya está registrado
     */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(),
                request.documentNumber())) {
            throw rejectDuplicate();
        }
        User user = userMapper.toEntity(request);
        user.setStatus(UserStatus.ACTIVE);
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (!isDocumentUniqueViolation(ex)) {
                throw ex;
            }
            log.warn("Alta concurrente con documento duplicado detectada por la restricción única");
            throw rejectDuplicate();
        }
        auditEventService.recordUserCreated(saved.getId());
        log.info("User created: id={}", saved.getId());
        return userMapper.toResponse(saved);
    }

    /**
     * Consulta un usuario por su identificador interno, en cualquier estado.
     *
     * @param id identificador interno
     * @return proyección pública del usuario
     * @throws UserNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    /**
     * Consulta un usuario por su documento compuesto (tipo + número), en cualquier estado.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return proyección pública del usuario
     * @throws UserNotFoundException si no existe (el mensaje no incluye el documento)
     */
    @Transactional(readOnly = true)
    public UserResponse findByDocument(DocumentType documentType, String documentNumber) {
        return userRepository.findByDocumentTypeAndDocumentNumber(documentType, documentNumber)
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::byDocument);
    }

    /**
     * Indica si el usuario puede iniciar un nuevo vínculo de canal: solo los usuarios {@code ACTIVE}
     * son elegibles; {@code SUSPENDED} e {@code INACTIVE} no lo son.
     *
     * @param id identificador interno
     * @return {@code userId} y {@code eligible}
     * @throws UserNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public EligibilityResponse checkEligibility(Long id) {
        User user = getUser(id);
        return new EligibilityResponse(user.getId(), user.getStatus() == UserStatus.ACTIVE);
    }

    /**
     * Actualiza los datos permitidos ({@code displayName}, {@code email}) de un usuario, en cualquier
     * estado del ciclo de vida. Los campos omitidos conservan su valor; la identidad legal, el estado y
     * {@code createdAt} no se modifican.
     *
     * <p>Si hay cambios efectivos se renueva {@code updatedAt} y se registra {@code MODIFICACION_DATOS}
     * en la misma transacción, solo con los nombres de los campos cambiados (nunca sus valores). Si los
     * valores informados coinciden con los actuales, no hay modificación ni evento de auditoría.</p>
     *
     * @param id      identificador interno
     * @param request datos permitidos ya validados (al menos uno informado)
     * @return proyección pública del usuario con los cambios guardados
     * @throws UserNotFoundException si no existe
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getUser(id);
        List<String> modifiedFields = modifiedFields(user, request);
        if (modifiedFields.isEmpty()) {
            return userMapper.toResponse(user);
        }
        userMapper.updateEntity(request, user);
        User saved = userRepository.saveAndFlush(user);
        auditEventService.recordDataModified(saved.getId(), modifiedFields);
        log.info("User data modified: id={} fields={}", saved.getId(), modifiedFields);
        return userMapper.toResponse(saved);
    }

    private static List<String> modifiedFields(User user, UpdateUserRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.getDisplayName() != null && !Objects.equals(request.getDisplayName(), user.getDisplayName())) {
            fields.add(FIELD_DISPLAY_NAME);
        }
        if (request.getEmail() != null && !Objects.equals(request.getEmail(), user.getEmail())) {
            fields.add(FIELD_EMAIL);
        }
        return fields;
    }

    private UserAlreadyExistsException rejectDuplicate() {
        auditEventService.recordCreationRejected(AuditRejectionReason.DUPLICATE_DOCUMENT);
        return new UserAlreadyExistsException();
    }

    private static boolean isDocumentUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toLowerCase(Locale.ROOT).contains(User.UK_DOCUMENT);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> UserNotFoundException.byId(id));
    }
}
