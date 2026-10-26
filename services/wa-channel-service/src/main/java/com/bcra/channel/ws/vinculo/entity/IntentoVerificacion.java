package com.bcra.channel.ws.vinculo.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resultado de un intento, asociado por FK al vínculo del canal. */
@Entity
@Table(name = "intento_verificacion")
@Getter
@Setter
@NoArgsConstructor
public class IntentoVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vinculo_id", nullable = false)
    private UUID vinculoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vinculo_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_intento_vinculo"))
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private Vinculo vinculo;

    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResultadoIntento resultado;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof IntentoVerificacion entidad && getId() != null && getId().equals(entidad.getId());
    }

    @Override
    public int hashCode() {
        return IntentoVerificacion.class.hashCode();
    }
}

