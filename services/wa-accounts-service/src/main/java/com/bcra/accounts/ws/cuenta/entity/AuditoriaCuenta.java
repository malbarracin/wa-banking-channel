package com.bcra.accounts.ws.cuenta.entity;

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

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de AuditoriaCuenta. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "auditorias_cuenta")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditoriaAccion accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditoriaResultado resultado;

    @Column(nullable = false, length = 30)
    private String numeroWhatsapp;

    @Column(nullable = false, length = 20)
    private String documentoTipo;

    @Column(nullable = false, length = 30)
    private String documentoNumero;

    @Column(nullable = true)
    private UUID cuentaId;

    @Column(nullable = true)
    private UUID transferenciaId;

    @Column(length = 255)
    private String motivo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant fecha;
}

