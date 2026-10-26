package com.bcra.users.ws.user.service;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.UserResponse;
import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.exception.DuplicateUserException;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Casos de uso del usuario con unicidad por documento y auditoría del resultado. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Crea un usuario activo y audita después de confirmar la transacción de alta.
     * @param solicitud datos mínimos validados por la API
     * @return usuario persistido
     * @throws DuplicateUserException si el documento ya está reservado
     */
    public UserResponse alta(CreateUserRequest solicitud) {
        User usuario;
        try {
            usuario = new TransactionTemplate(transactionManager).execute(estado -> {
                if (tieneDocumento(solicitud)) {
                    throw new DuplicateUserException();
                }
                return userRepository.saveAndFlush(userMapper.toEntity(solicitud));
            });
        } catch (DuplicateUserException exception) {
            auditar(solicitud, AuditResult.RECHAZADO, "Documento duplicado");
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            // Una carrera de altas se resuelve por la restricción única, tras revertir la transacción.
            if (!tieneDocumento(solicitud)) {
                throw exception;
            }
            auditar(solicitud, AuditResult.RECHAZADO, "Documento duplicado");
            throw new DuplicateUserException();
        }
        auditar(solicitud, AuditResult.EXITOSO, "");
        return userMapper.toResponse(Objects.requireNonNull(usuario));
    }

    private boolean tieneDocumento(CreateUserRequest solicitud) {
        return userRepository.findByDocumentoTipoAndDocumentoNumero(
                solicitud.documento().tipo(), solicitud.documento().numero()).isPresent();
    }

    private void auditar(CreateUserRequest solicitud, AuditResult resultado, String motivo) {
        auditService.registrar(AuditAction.ALTA, resultado,
                solicitud.documento().tipo(), solicitud.documento().numero(), motivo);
    }
}
