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

/** Registro persistente de Bloqueo; no expone datos sensibles mediante toString. */
@Entity
@Table(name = "bloqueos")
@Getter
@Setter
@NoArgsConstructor
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tarjetaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoBloqueo tipo;

    @Column()
    private String motivo;

    @Column(nullable = false)
    private Instant fechaBloqueo;

    @Column()
    private Instant fechaDesbloqueo;

    @Column(nullable = false)
    private String usuarioEjecutor;

    @Column(nullable = false)
    private boolean dobleConfirmacionCompleta;
}

