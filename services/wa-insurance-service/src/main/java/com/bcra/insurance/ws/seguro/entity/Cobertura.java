package com.bcra.insurance.ws.seguro.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import lombok.Builder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cobertura de una póliza, conservada sin propagar cambios hacia la póliza. */
@Entity
@Table(name = "cobertura")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cobertura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false)
    private Poliza poliza;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false, columnDefinition = "text")
    private String resumen;

    @Builder
    private Cobertura(Poliza poliza, String descripcion, String resumen) {
        this.poliza = poliza;
        this.descripcion = descripcion;
        this.resumen = resumen;
    }
}
