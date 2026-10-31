package com.bcra.channel.ws.vinculo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/** Consultas de vínculos por número y por identidad del cliente. */
public interface VinculoRepository extends JpaRepository<Vinculo, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vinculo v where v.numeroWhatsapp = :numero order by v.createdAt, v.id")
    List<Vinculo> bloquearPorNumero(String numero);

    Optional<Vinculo> findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(String numeroWhatsapp);

    List<Vinculo> findByNumeroWhatsappAndEstado(String numeroWhatsapp, VinculoEstado estado);

    List<Vinculo> findByDocumentoTipoAndDocumentoNumeroOrderByFechaUltimoCambioEstadoDesc(
            String documentoTipo, String documentoNumero);
}
