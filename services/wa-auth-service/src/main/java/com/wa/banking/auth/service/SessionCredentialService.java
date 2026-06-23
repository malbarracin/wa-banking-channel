package com.wa.banking.auth.service;

import com.wa.banking.auth.api.v1.dto.AuditEntryResponse;
import com.wa.banking.auth.api.v1.dto.CredentialStatusResponse;
import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RenewCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RevokeByUserRequest;
import com.wa.banking.auth.api.v1.dto.RevokeCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialResponse;
import com.wa.banking.auth.entity.AuditActor;
import com.wa.banking.auth.entity.RevokeReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrato del servicio de credenciales de sesión del canal (H2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface SessionCredentialService {

    IssueCredentialResponse issue(IssueCredentialRequest request);

    RenewCredentialResponse renew(String credentialId);

    void revoke(String credentialId, RevokeReason reason, AuditActor actor);

    void revokeWithReason(String credentialId, RevokeCredentialRequest request, AuditActor actor);

    int revokeByUser(RevokeByUserRequest request);

    ValidateCredentialResponse validate(ValidateCredentialRequest request);

    CredentialStatusResponse getStatus(String credentialId);

    Page<AuditEntryResponse> getAuditHistory(String credentialId, Pageable pageable);
}
