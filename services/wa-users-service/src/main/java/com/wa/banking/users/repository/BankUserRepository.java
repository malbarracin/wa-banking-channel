package com.wa.banking.users.repository;

import com.wa.banking.users.entity.BankUserEntity;
import com.wa.banking.users.entity.DocumentType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio Spring Data MongoDB para usuarios bancarios.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface BankUserRepository extends MongoRepository<BankUserEntity, String> {

    Optional<BankUserEntity> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
