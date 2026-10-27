package com.bcra.accounts.ws.cuenta.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Modelo persistente de TerceroAgendado. Las referencias al titular no tienen FK a otros servicios. */
@Entity
@Table(name = "terceros_agendados")
@Getter
@Setter
@NoArgsConstructor
public class TerceroAgendado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String titularDocumentoTipo;

    @Column(nullable = false, length = 30)
    private String titularDocumentoNumero;

    @Column(nullable = false, length = 100)
    private String alias;

    @Column(nullable = false, length = 34)
    private String numeroCuentaDestino;

    @Column(length = 100)
    private String bancoDestino;

    @Column(nullable = false)
    private boolean activo;
}

