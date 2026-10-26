package com.bcra.users.ws.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Documento identificador; el catálogo de tipos permanece abierto. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Documento {

    @NotBlank
    @Column(name = "documento_tipo", nullable = false)
    private String tipo;

    @NotBlank
    @Column(name = "documento_numero", nullable = false)
    private String numero;
}
