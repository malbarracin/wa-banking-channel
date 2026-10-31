package com.bcra.accounts.ws.cuenta.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.accounts.ws.cuenta.entity.TerceroAgendado;

/** Acceso a persistencia de TerceroAgendado. */
public interface TerceroAgendadoRepository extends JpaRepository<TerceroAgendado, UUID> {

    List<TerceroAgendado> findByTitularDocumentoTipoAndTitularDocumentoNumeroAndActivoTrue(
            String tipo, String numero);
}

