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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evento independiente que permite auditar incluso solicitudes rechazadas. */
@Entity
@Table(name = "credencial_audit_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CredencialAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, updatable = false, length = 30)
    private AuditAccion accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, updatable = false, length = 20)
    private AuditResultado resultado;

    @Column(name = "numero_whatsapp", nullable = false, updatable = false, length = 20)
    private String numeroWhatsapp;

    @Column(name = "documento_tipo", updatable = false, length = 30)
    private String documentoTipo;

    @Column(name = "documento_numero", updatable = false, length = 50)
    private String documentoNumero;

    @Column(name = "motivo", updatable = false, length = 255)
    private String motivo;

    @Column(name = "jti", updatable = false, length = 255)
    private String jti;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

}

