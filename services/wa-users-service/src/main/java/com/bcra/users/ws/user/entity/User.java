package com.bcra.users.ws.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @ToString.Include
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private DocumentType tipoDocumento;
    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;
    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;
    @Column(name = "nombre_tratamiento")
    private String nombreTratamiento;
    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, length = 64)
    private String telefono;
    @Column(length = 2048)
    private String preferencias;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ToString.Include
    private UserStatus estado;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
