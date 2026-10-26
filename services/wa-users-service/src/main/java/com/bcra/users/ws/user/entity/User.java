package com.bcra.users.ws.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Usuario persistido; su representación textual solo incluye identificador y estado. */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = "uk_users_documento", columnNames = {"documento_tipo", "documento_numero"}))
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    @ToString.Include
    private UUID id;

    @Embedded
    @Valid
    @NotNull
    private Documento documento;

    @NotBlank
    @Column(nullable = false)
    private String nombre;

    @NotBlank
    @Column(nullable = false)
    private String apellido;

    @NotNull
    @Column(nullable = false)
    private LocalDate fechaNacimiento;

    private String email;

    private String telefono;

    private String nombreTratamiento;

    /** Preferencias generales no sensibles, almacenadas como texto. */
    @Column(columnDefinition = "text")
    private String preferencias;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ToString.Include
    private UserStatus estado = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof User user && getId() != null && getId().equals(user.getId());
    }

    @Override
    public int hashCode() {
        // El hash permanece estable cuando JPA asigna el identificador.
        return User.class.hashCode();
    }
}
