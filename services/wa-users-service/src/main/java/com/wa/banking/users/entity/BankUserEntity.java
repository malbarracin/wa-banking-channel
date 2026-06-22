package com.wa.banking.users.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Entidad MongoDB que representa un usuario cliente del banco.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bank_users")
@CompoundIndex(name = "uk_document", def = "{'documentType': 1, 'documentNumber': 1}", unique = true)
public class BankUserEntity {

    @Id
    private String id;

    private DocumentType documentType;

    private String documentNumber;

    private String displayName;

    private String email;

    private String phone;

    private Map<String, Object> preferences;

    private UserStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
