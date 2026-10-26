package com.bcra.credentials.ws.credencial.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.client.ClienteNoEncontradoException;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.client.UsersServiceUnavailableException;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.mapper.CredencialMapper;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;
import com.bcra.credentials.ws.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Autoriza únicamente credenciales firmadas, persistidas, vigentes y de clientes activos en H1. */
@Service
@RequiredArgsConstructor
public class ValidacionService {

    private final JwtService jwtService;
    private final CredencialRepository repository;
    private final CredencialMapper mapper;
    private final UsersServiceClient usersServiceClient;
    private final CredencialAuditService auditService;

    /** @return identidad persistida; cualquier rechazo de autenticación se audita independientemente. */
    public ValidarCredencialResponse validar(String token) {
        Optional<CredencialSesion> encontrada = Optional.empty();
        try {
            Jwt jwt = jwtService.verificar(token);
            if (jwt.getId() == null || jwt.getId().isBlank() || jwt.getExpiresAt() == null
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(Instant.now())) {
                throw new UnauthorizedException();
            }
            encontrada = repository.findByJti(jwt.getId());
            CredencialSesion credencial = encontrada.orElseThrow(UnauthorizedException::new);
            Instant ahora = Instant.now();
            if (credencial.getEstado() != CredencialEstado.ACTIVA || credencial.isRiesgo()
                    || credencial.getFechaRevocacion() != null
                    || !credencial.getFechaExpiracion().isAfter(ahora) || !jwt.getExpiresAt().isAfter(ahora)
                    || credencial.getFechaEmision().isAfter(ahora)
                    || !credencial.getNumeroWhatsapp().equals(jwt.getSubject())
                    || !(credencial.getDocumentoTipo() + ":" + credencial.getDocumentoNumero())
                            .equals(jwt.getClaims().get("doc"))
                    || jwt.getExpiresAt().getEpochSecond() != credencial.getFechaExpiracion().getEpochSecond()) {
                throw new UnauthorizedException();
            }
            if (usersServiceClient.consultarEstado(credencial.getDocumentoTipo(), credencial.getDocumentoNumero())
                    .estado() != EstadoCliente.ACTIVE) {
                throw new UnauthorizedException();
            }
            return mapper.toValidarResponse(credencial);
        } catch (UnauthorizedException | ClienteNoEncontradoException | UsersServiceUnavailableException exception) {
            auditService.registrarValidacionFallida(encontrada);
            throw new UnauthorizedException();
        }
    }
}
