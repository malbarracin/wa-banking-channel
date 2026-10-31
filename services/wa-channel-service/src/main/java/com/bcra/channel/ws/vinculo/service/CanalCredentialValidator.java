package com.bcra.channel.ws.vinculo.service;

import org.springframework.stereotype.Service;

import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.dto.CanalCredentialToken;

import lombok.RequiredArgsConstructor;

/** Delega firma, emisor y revocación a H2 y exige sesión vigente para el número solicitado. */
@Service
@RequiredArgsConstructor
public class CanalCredentialValidator {
    private final CredencialesServiceClient credencialesServiceClient;

    /** Solo H2 puede confirmar que el token autoriza al titular del número. */
    public boolean validar(CanalCredentialToken token, String numeroWhatsapp) {
        return credencialesServiceClient.validar(token.valor(), numeroWhatsapp);
    }
}
