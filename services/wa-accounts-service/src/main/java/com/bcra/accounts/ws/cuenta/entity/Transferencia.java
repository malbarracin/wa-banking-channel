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

import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de Transferencia. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "transferencias")
@Check(constraints = "(cuenta_destino_id is not null and tercero_id is null) or "
        + "(cuenta_destino_id is null and tercero_id is not null)")
@Getter
@Setter
@NoArgsConstructor
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_origen_id", nullable = false)
    private Cuenta cuentaOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cuenta_destino_id", nullable = true)
    private Cuenta cuentaDestino;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "tercero_id", nullable = true)
    private TerceroAgendado tercero;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Moneda moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoTransferencia estado;

    @Column(length = 100)
    private String referencia;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Vencimiento de la preparación; nullable para registros históricos previos a este flujo. */
    @Column
    private Instant expiraEn;

    @Column(length = 255)
    private String concepto;

    @Column(nullable = true)
    private Instant fechaEjecucion;

    @Column(length = 255)
    private String motivoRechazo;

    @Column(nullable = false)
    private boolean dobleConfirmacionRequerida;
}

