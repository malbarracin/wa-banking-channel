package com.bcra.credentials.ws.credencial.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Credencial persistida; conserva identificadores externos, nunca el token ni secretos. */
@Entity
@Table(name = "credencial_sesion")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CredencialSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "jti", nullable = false, unique = true, updatable = false, length = 255)
    private String jti;

    @Column(name = "numero_whatsapp", nullable = false, updatable = false, length = 20)
    private String numeroWhatsapp;

    @Column(name = "documento_tipo", nullable = false, updatable = false, length = 30)
    private String documentoTipo;

    @Column(name = "documento_numero", nullable = false, updatable = false, length = 50)
    private String documentoNumero;

    @Column(name = "vinculo_id", nullable = false, updatable = false)
    private UUID vinculoId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private CredencialEstado estado;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private Instant fechaEmision;

    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Setter
    @Column(name = "fecha_revocacion")
    private Instant fechaRevocacion;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revocacion", length = 30)
    private MotivoRevocacion motivoRevocacion;

    @Setter
    @Column(name = "riesgo", nullable = false)
    private boolean riesgo;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "revocada_por", length = 20)
    private RevocadaPor revocadaPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void inicializarAuditoria() {
        Instant ahora = Instant.now();
        createdAt = ahora;
        updatedAt = ahora;
    }

    @PreUpdate
    void actualizarAuditoria() {
        updatedAt = Instant.now();
    }
}

