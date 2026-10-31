package com.bcra.credentials.ws.credencial.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Datos durables para repetir una entrega; nunca almacena el JWT ni la clave de firma. */
@Entity
@Table(name = "entrega_renovacion")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EntregaRenovacion {

    @Id
    @Column(name = "anterior_id", nullable = false, updatable = false)
    private UUID anteriorId;

    @Column(nullable = false, unique = true, updatable = false)
    private String jti;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private Instant fechaEmision;

    @Column(name = "fecha_expiracion", nullable = false, updatable = false)
    private Instant fechaExpiracion;
}
