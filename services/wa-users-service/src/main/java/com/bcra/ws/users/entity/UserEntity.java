package com.bcra.ws.users.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Documento persistido del usuario bancario en la colección {@code users}.
 * Unicidad: índice compuesto {@code (documentType, documentNumber)}.
 *
 * @since 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@CompoundIndex(name = "uk_document", def = "{'documentType': 1, 'documentNumber': 1}", unique = true)
public class UserEntity {

    @Id
    private String id;

    private DocumentType documentType;

    private String documentNumber;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private String preferredName;

    private String preferredLanguage;

    private UserStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
