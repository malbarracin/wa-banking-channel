package com.bcra.users.ws.user.service;

import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.users.ws.user.entity.User;
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
}
