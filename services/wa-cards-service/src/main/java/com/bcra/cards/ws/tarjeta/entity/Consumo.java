package com.bcra.cards.ws.tarjeta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro persistente de Consumo; no expone datos sensibles mediante toString. */
@Entity
@Table(name = "consumos")
@Getter
@Setter
@NoArgsConstructor
public class Consumo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tarjetaId;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false)
    private String comercio;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column()
    private String descripcion;

    @Column()
    private String referencia;
}

