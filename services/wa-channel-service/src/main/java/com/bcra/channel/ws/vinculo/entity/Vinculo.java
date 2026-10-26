package com.bcra.channel.ws.vinculo.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vínculo local al canal; el documento referencia H1 sin relación entre bases. */
@Entity
@Table(name = "vinculo")
@Getter
@Setter
@NoArgsConstructor
public class Vinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$")
    @Column(name = "numero_whatsapp", nullable = false, length = 16)
    private String numeroWhatsapp;

    @Column(name = "documento_tipo")
    private String documentoTipo;

    @Column(name = "documento_numero")
    private String documentoNumero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VinculoEstado estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_bloqueo", length = 32)
    private MotivoBloqueo motivoBloqueo;

    @Column(name = "terminos_aceptados", nullable = false)
    private boolean terminosAceptados;

    @Column(name = "fecha_aceptacion_terminos")
    private Instant fechaAceptacionTerminos;

    @Embedded
    private Preferencias preferencias = new Preferencias();

    @Column(name = "fecha_activacion")
    private Instant fechaActivacion;

    @Column(name = "fecha_ultimo_cambio_estado", nullable = false)
    private Instant fechaUltimoCambioEstado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Vinculo entidad && getId() != null && getId().equals(entidad.getId());
    }

    @Override
    public int hashCode() {
        return Vinculo.class.hashCode();
    }

    @PrePersist
    void registrarCreacion() {
        Instant ahora = Instant.now();
        createdAt = ahora;
        updatedAt = ahora;
        if (fechaUltimoCambioEstado == null) {
            fechaUltimoCambioEstado = ahora;
        }
    }

    @PreUpdate
    void registrarActualizacion() {
        updatedAt = Instant.now();
    }
}

