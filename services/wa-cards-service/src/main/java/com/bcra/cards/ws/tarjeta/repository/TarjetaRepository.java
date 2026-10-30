package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas de persistencia de Tarjeta. */
public interface TarjetaRepository extends JpaRepository<Tarjeta, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tarjeta t where t.id = :id and t.titularDocumentoTipo = :tipo "
            + "and t.titularDocumentoNumero = :numero")
    Optional<Tarjeta> buscarParaBloqueo(@Param("id") UUID id, @Param("tipo") String tipo,
            @Param("numero") String numero);

    List<Tarjeta> findByTitularDocumentoTipoAndTitularDocumentoNumero(String documentoTipo, String documentoNumero);

    Optional<Tarjeta> findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
            UUID id, String documentoTipo, String documentoNumero);
}

