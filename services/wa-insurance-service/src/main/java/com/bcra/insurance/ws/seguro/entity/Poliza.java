package com.bcra.insurance.ws.seguro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Builder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Póliza del registro bancario; la identidad del titular no tiene FK a otros servicios. */
@Entity
@Table(name = "poliza")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poliza {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String numeroPoliza;

    @Column(nullable = false)
    private String ramo;

    @Column(nullable = false)
    private boolean vigente;

    @Column(nullable = false)
    private LocalDate fechaVigenciaDesde;

    @Column(nullable = false)
    private LocalDate fechaVigenciaHasta;

    @Column(nullable = false)
    private String titularDocumentoTipo;

    @Column(nullable = false)
    private String titularDocumentoNumero;

    private String aseguradoDocumentoTipo;

    private String aseguradoDocumentoNumero;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @Builder
    private Poliza(String numeroPoliza, String ramo, boolean vigente, LocalDate fechaVigenciaDesde,
            LocalDate fechaVigenciaHasta, String titularDocumentoTipo, String titularDocumentoNumero) {
        this.numeroPoliza = numeroPoliza;
        this.ramo = ramo;
        this.vigente = vigente;
        this.fechaVigenciaDesde = fechaVigenciaDesde;
        this.fechaVigenciaHasta = fechaVigenciaHasta;
        this.titularDocumentoTipo = titularDocumentoTipo;
        this.titularDocumentoNumero = titularDocumentoNumero;
    }
}
