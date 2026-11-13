package com.ingenia.wa.users.service;

import java.util.List;

import com.ingenia.wa.users.api.v1.dto.UserAuditEventResponse;
import com.ingenia.wa.users.api.v1.dto.UserCreateRequest;
import com.ingenia.wa.users.api.v1.dto.UserResponse;
import com.ingenia.wa.users.api.v1.dto.UserStatusChangeRequest;
import com.ingenia.wa.users.api.v1.dto.UserUpdateRequest;
import com.ingenia.wa.users.domain.DocumentType;

/**
 * Contrato de negocio del dominio Usuarios (U1–U4).
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public interface UserService {

    /**
     * Alta de usuario (U1). Estado inicial ACTIVE.
     *
     * @param request datos mínimos
     * @param actor   actor opcional
     * @return usuario creado
     */
    UserResponse create(UserCreateRequest request, String actor);

    /**
     * Consulta por id interno (U2).
     *
     * @param id id interno
     * @return usuario
     */
    UserResponse findById(String id);

    /**
     * Consulta por documento (U2).
     *
     * @param documentType   tipo
     * @param documentNumber número
     * @return usuario
     */
    UserResponse findByDocument(DocumentType documentType, String documentNumber);

    /**
     * Actualización de campos permitidos (U3).
     *
     * @param id      id interno
     * @param request campos a actualizar
     * @param actor   actor opcional
     * @return usuario actualizado
     */
    UserResponse update(String id, UserUpdateRequest request, String actor);

    /**
     * Cambio de estado con validación de transiciones (U4).
     *
     * @param id      id interno
     * @param request estado destino
     * @param actor   actor opcional
     * @return usuario con nuevo estado
     */
    UserResponse changeStatus(String id, UserStatusChangeRequest request, String actor);

    /**
     * Historial de auditoría del usuario.
     *
     * @param id id interno
     * @return eventos ordenados desc
     */
    List<UserAuditEventResponse> findAuditTrail(String id);
}
