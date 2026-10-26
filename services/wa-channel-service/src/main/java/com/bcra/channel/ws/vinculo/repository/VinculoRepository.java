package com.bcra.channel.ws.vinculo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/** Consultas de vínculos por número y por identidad del cliente. */
public interface VinculoRepository extends JpaRepository<Vinculo, UUID> {

    Optional<Vinculo> findFirstByNumeroWhatsappOrderByFechaUltimoCambioEstadoDesc(String numeroWhatsapp);

    List<Vinculo> findByNumeroWhatsappAndEstado(String numeroWhatsapp, VinculoEstado estado);

    List<Vinculo> findByDocumentoTipoAndDocumentoNumeroOrderByFechaUltimoCambioEstadoDesc(
            String documentoTipo, String documentoNumero);
}

