package com.bank.users.user.entity;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Registro persistente del cliente; el contacto no representa un vínculo de canal. */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = "uk_users_documento", columnNames = {"tipo_documento", "numero_documento"}))
@Getter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;

    @Setter
    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @Setter
    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    @Setter
    @Column(name = "nombre_tratamiento", nullable = false)
    private String nombreTratamiento;

    @Setter
    private String email;

    @Setter
    @Column(name = "telefono_contacto")
    private String telefonoContacto;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> preferencias = Map.of();

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ToString.Include
    private UserStatus status = UserStatus.ACTIVO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Devuelve una vista inmodificable de las preferencias generales no sensibles. */
    public Map<String, Object> getPreferencias() {
        return Map.copyOf(preferencias);
    }

    /** Copia las preferencias para no retener el mapa mutable del llamador. */
    public void setPreferencias(Map<String, Object> preferencias) {
        this.preferencias = Map.copyOf(Objects.requireNonNull(preferencias, "preferencias"));
    }
}
