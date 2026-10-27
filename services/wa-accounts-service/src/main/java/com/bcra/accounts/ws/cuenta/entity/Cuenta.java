package com.bcra.accounts.ws.cuenta.entity;

import java.math.BigDecimal;
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
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de Cuenta. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 34)
    private String numeroCuenta;

    @Column(nullable = false, length = 100)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoCuenta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Moneda moneda;

    @Column(nullable = false, length = 20)
    private String titularDocumentoTipo;

    @Column(nullable = false, length = 30)
    private String titularDocumentoNumero;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoDisponible;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoContable;

    @Column(nullable = false)
    private boolean activa;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}

