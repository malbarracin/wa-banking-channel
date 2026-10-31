package com.bcra.channel.ws.vinculo.entity;

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

/** Evento independiente del vínculo, sin relaciones que impidan auditar operaciones rechazadas. */
@Entity
@Table(name = "vinculo_audit_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VinculoAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAccion accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditResultado resultado;

    @Column(nullable = false, updatable = false)
    private String numeroWhatsapp;

    @Column(updatable = false)
    private String documentoTipo;

    @Column(updatable = false)
    private String documentoNumero;

    @Column(updatable = false)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private Instant fecha;
}
