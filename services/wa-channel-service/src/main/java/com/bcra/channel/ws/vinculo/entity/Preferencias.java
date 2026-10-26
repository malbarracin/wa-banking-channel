package com.bcra.channel.ws.vinculo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Preferencias propias del canal; el horario es opcional. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Preferencias {

    @Column(name = "idioma")
    private String idioma;

    @Column(name = "notificaciones_habilitadas", nullable = false)
    private boolean notificacionesHabilitadas;

    @Column(name = "horario_preferido")
    private String horarioPreferido;
}

