package com.bcra.insurance.ws.seguro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos mínimos de la denuncia; el estado procede del sistema de siniestros. */
@Entity
@Table(name = "siniestro")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Siniestro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String numeroCaso;

    @Column(nullable = false)
    private UUID polizaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoSiniestro tipoSiniestro;

    @Column(nullable = false)
    private LocalDate fechaSiniestro;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSiniestro estado;

    /** Acción pendiente informada por el sistema de siniestros; si es nula se deriva del estado. */
    @Column(length = 500)
    private String pendiente;

    @ElementCollection
    @CollectionTable(name = "siniestro_documento_pendiente", joinColumns = @JoinColumn(name = "siniestro_id"))
    @Column(name = "documento", nullable = false)
    private List<String> documentosPendientes = new ArrayList<>();

    @Column(nullable = false)
    private Instant fechaApertura;

    @Column(nullable = false)
    private Instant ventanaDuplicadoHasta;

    @Builder
    private Siniestro(String numeroCaso, UUID polizaId, TipoSiniestro tipoSiniestro, LocalDate fechaSiniestro,
            String descripcion, EstadoSiniestro estado, String pendiente, List<String> documentosPendientes,
            Instant fechaApertura, Instant ventanaDuplicadoHasta) {
        this.numeroCaso = numeroCaso;
        this.polizaId = polizaId;
        this.tipoSiniestro = tipoSiniestro;
        this.fechaSiniestro = fechaSiniestro;
        this.descripcion = descripcion;
        this.estado = estado;
        this.pendiente = pendiente;
        this.documentosPendientes = documentosPendientes == null ? new ArrayList<>() : new ArrayList<>(documentosPendientes);
        this.fechaApertura = fechaApertura;
        this.ventanaDuplicadoHasta = ventanaDuplicadoHasta;
    }
}
