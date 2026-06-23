package com.wa.banking.auth.repository;

import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.entity.SessionCredentialEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio MongoDB para credenciales de sesión del canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface SessionCredentialRepository extends MongoRepository<SessionCredentialEntity, String> {

    Optional<SessionCredentialEntity> findByChannelLinkIdAndStatus(String channelLinkId, CredentialStatus status);

    List<SessionCredentialEntity> findByBankUserIdAndStatus(String bankUserId, CredentialStatus status);
}
