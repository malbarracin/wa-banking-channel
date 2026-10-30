package com.bcra.cards.ws.tarjeta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro persistente de AuditoriaTarjeta; no expone datos sensibles mediante toString. */
@Entity
@Table(name = "auditoria_tarjetas")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaTarjeta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditoriaAccion accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditoriaResultado resultado;

    @Column(nullable = false)
    private String numeroWhatsapp;

    @Column(nullable = false)
    private String documentoTipo;

    @Column(nullable = false)
    private String documentoNumero;

    @Column()
    private UUID tarjetaId;

    @Column()
    private String motivo;

    @Column(nullable = false)
    private Instant fecha;
}

