package com.bank.users.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
