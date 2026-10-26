package com.bcra.credentials.ws.credencial.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;

/** Acceso a credenciales por identidad externa y estado. */
public interface CredencialRepository extends JpaRepository<CredencialSesion, UUID> {

    Optional<CredencialSesion> findFirstByNumeroWhatsappOrderByFechaEmisionDesc(String numeroWhatsapp);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialSesion c where c.numeroWhatsapp = :numero "
            + "and c.estado = com.bcra.credentials.ws.credencial.entity.CredencialEstado.ACTIVA")
    Optional<CredencialSesion> findActivaParaRevocar(@Param("numero") String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialSesion c where c.estado = :estado order by c.id")
    List<CredencialSesion> buscarPorEstadoParaRevocar(@Param("estado") CredencialEstado estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialSesion c where c.estado = :estado "
            + "and c.documentoTipo = :tipo and c.documentoNumero = :numero order by c.id")
    List<CredencialSesion> buscarPorClienteParaRevocar(@Param("estado") CredencialEstado estado,
            @Param("tipo") String tipo, @Param("numero") String numero);

    Optional<CredencialSesion> findByNumeroWhatsappAndEstado(String numeroWhatsapp, CredencialEstado estado);

    Optional<CredencialSesion> findByJti(String jti);

    List<CredencialSesion> findByDocumentoTipoAndDocumentoNumero(String documentoTipo, String documentoNumero);
}

