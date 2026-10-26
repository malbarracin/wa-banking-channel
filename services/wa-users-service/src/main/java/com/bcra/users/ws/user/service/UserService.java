package com.bcra.users.ws.user.service;

import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.dto.UpdateStatusRequest;
import com.bcra.users.ws.user.exception.InvalidStatusTransitionException;
import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.dto.UpdateUserRequest;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.exception.UserNotFoundException;
import com.bcra.users.ws.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Casos de uso de consulta y actualización de usuarios bancarios por documento. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Aplica solo las transiciones definidas para este hito y audita después del commit o rollback.
     * @return usuario con el estado guardado
     * @throws UserNotFoundException si no existe el documento
     * @throws InvalidStatusTransitionException si falta el estado o la transición no está soportada
     */
    public User cambiarEstado(String tipo, String numero, UpdateStatusRequest request) {
        User actualizado;
        try {
            actualizado = Objects.requireNonNull(transactionTemplate.execute(status -> {
                User usuario = consultar(tipo, numero);
                if (request == null || !puedeCambiarEstado(usuario.getEstado(), request.estado())) {
                    throw new InvalidStatusTransitionException();
                }
                usuario.setEstado(request.estado());
                return repository.saveAndFlush(usuario);
            }));
        } catch (InvalidStatusTransitionException | UserNotFoundException
                | DataAccessException | TransactionException exception) {
            auditService.registrar(AuditAction.CAMBIO_ESTADO, AuditResult.RECHAZADO, tipo, numero,
                    "No se pudo cambiar el estado del usuario");
            throw exception;
        }
        auditService.registrar(AuditAction.CAMBIO_ESTADO, AuditResult.EXITOSO, tipo, numero, "");
        // Punto de extensión para notificar a servicios dependientes tras confirmar el cambio de estado.
        return actualizado;
    }

    /** Actualiza solo datos permitidos y audita el resultado después del commit o rollback. */
    public User actualizar(String tipo, String numero, UpdateUserRequest request) {
        User actualizado;
        try {
            actualizado = Objects.requireNonNull(transactionTemplate.execute(status -> {
                User usuario = consultar(tipo, numero);
                mapper.actualizar(request, usuario);
                return repository.saveAndFlush(usuario);
            }));
        } catch (UserNotFoundException | DataAccessException | TransactionException exception) {
            auditService.registrar(AuditAction.ACTUALIZACION, AuditResult.RECHAZADO, tipo, numero,
                    "No se pudo actualizar el usuario");
            throw exception;
        }
        auditService.registrar(AuditAction.ACTUALIZACION, AuditResult.EXITOSO, tipo, numero, "");
        return actualizado;
    }

    /**
     * Consulta por ambos componentes del documento, incluyendo usuarios no activos.
     * @return usuario encontrado
     * @throws UserNotFoundException cuando no existe el documento solicitado
     */
    @Transactional(readOnly = true)
    public User consultar(String tipo, String numero) {
        return repository.findByDocumentoTipoAndDocumentoNumero(tipo, numero)
                .orElseThrow(UserNotFoundException::new);
    }

    private boolean puedeCambiarEstado(UserStatus actual, UserStatus solicitado) {
        return switch (actual) {
            case ACTIVE -> solicitado == UserStatus.SUSPENDED || solicitado == UserStatus.LOGICAL_DELETE;
            case SUSPENDED -> solicitado == UserStatus.ACTIVE || solicitado == UserStatus.LOGICAL_DELETE;
            case LOGICAL_DELETE -> false;
        };
    }
}
