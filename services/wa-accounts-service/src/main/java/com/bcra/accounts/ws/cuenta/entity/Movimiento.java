package com.bcra.accounts.ws.cuenta.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de Movimiento. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "movimientos")
@Getter
@Setter
@NoArgsConstructor
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Moneda moneda;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(length = 100)
    private String referencia;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoResultante;
}

