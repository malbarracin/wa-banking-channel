package com.bank.users.user.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Cliente registrado en el Servicio de Usuarios.
 *
 * <p>La combinación {@code documentType} + {@code documentNumber} es única en la base de datos para
 * todos los estados (incluida la baja lógica). {@code createdAt} se fija al persistir y no se modifica;
 * {@code updatedAt} se renueva en cada actualización. {@code toString()} no incluye PII.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = User.UK_DOCUMENT, columnNames = {"document_type", "document_number"}))
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class User {

    /** Nombre de la restricción de unicidad del documento (tipo + número). */
    public static final String UK_DOCUMENT = "uk_users_document";

    private static final int ENUM_COLUMN_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = ENUM_COLUMN_LENGTH)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false)
    private String documentNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email")
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = ENUM_COLUMN_LENGTH)
    @ToString.Include
    private UserStatus status;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
