package com.bcra.cards.ws.tarjeta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro persistente de AvisoViaje; no expone datos sensibles mediante toString. */
@Entity
@Table(name = "avisos_viaje")
@Getter
@Setter
@NoArgsConstructor
public class AvisoViaje {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tarjetaId;

    @Column(nullable = false)
    private String pais;

    @Column()
    private String region;

    @Column(nullable = false)
    private LocalDate fechaDesde;

    @Column(nullable = false)
    private LocalDate fechaHasta;

    @Column(nullable = false)
    private Instant fechaRegistro;

    @Column(nullable = false)
    private boolean confirmado;
}

