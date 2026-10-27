package com.bcra.accounts.ws.cuenta.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de LimiteCanal. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "limites_canal", uniqueConstraints = @UniqueConstraint(columnNames = {"producto", "canal"}))
@Getter
@Setter
@NoArgsConstructor
public class LimiteCanal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String producto;

    @Column(nullable = false, length = 40)
    private String canal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limiteDiario;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limiteMensual;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal umbralDobleConfirmacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Moneda moneda;
}

