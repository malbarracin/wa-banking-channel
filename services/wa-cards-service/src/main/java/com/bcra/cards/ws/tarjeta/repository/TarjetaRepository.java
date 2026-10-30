package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de persistencia de Tarjeta. */
public interface TarjetaRepository extends JpaRepository<Tarjeta, UUID> {

    List<Tarjeta> findByTitularDocumentoTipoAndTitularDocumentoNumero(String documentoTipo, String documentoNumero);

    Optional<Tarjeta> findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
            UUID id, String documentoTipo, String documentoNumero);
}

