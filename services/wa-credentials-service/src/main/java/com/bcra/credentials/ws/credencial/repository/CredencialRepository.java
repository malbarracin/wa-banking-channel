package com.bcra.credentials.ws.credencial.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;

/** Acceso a credenciales por identidad externa y estado. */
public interface CredencialRepository extends JpaRepository<CredencialSesion, UUID> {

    Optional<CredencialSesion> findByNumeroWhatsappAndEstado(String numeroWhatsapp, CredencialEstado estado);

    Optional<CredencialSesion> findByJti(String jti);

    List<CredencialSesion> findByDocumentoTipoAndDocumentoNumero(String documentoTipo, String documentoNumero);
}

