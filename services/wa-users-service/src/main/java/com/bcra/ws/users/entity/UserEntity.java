package com.bcra.ws.users.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Documento persistido del usuario bancario en la colección {@code users}.
 * Unicidad: índice compuesto {@code (documentType, documentNumber)}.
 *
 * @since 2026-10-19
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@CompoundIndex(name = "uk_document", def = "{'documentType': 1, 'documentNumber': 1}", unique = true)
public class UserEntity {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    private DocumentType documentType;

    private String documentNumber;

    private String firstName;

    private String lastName;

    @Indexed(name = "uk_email", unique = true)
    private String email;

    private String phone;

    private String address;

    private LocalDate birthDate;

    private String preferredName;

    private String preferredLanguage;

    private UserStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
