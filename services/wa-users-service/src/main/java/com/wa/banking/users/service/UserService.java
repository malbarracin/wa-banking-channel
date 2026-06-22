package com.wa.banking.users.service;

import com.wa.banking.users.api.v1.dto.ChangeUserStatusRequestV1;
import com.wa.banking.users.api.v1.dto.CreateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UpdateUserRequestV1;
import com.wa.banking.users.api.v1.dto.UserAuditResponseV1;
import com.wa.banking.users.api.v1.dto.UserResponseV1;
import com.wa.banking.users.entity.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrato de servicio para operaciones de usuarios bancarios (flujos U1–U4).
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface UserService {

    UserResponseV1 create(CreateUserRequestV1 request);

    UserResponseV1 findById(String id);

    UserResponseV1 findByDocument(DocumentType documentType, String documentNumber);

    UserResponseV1 update(String id, UpdateUserRequestV1 request);

    UserResponseV1 changeStatus(String id, ChangeUserStatusRequestV1 request);

    Page<UserAuditResponseV1> getAuditHistory(String id, Pageable pageable);
}
