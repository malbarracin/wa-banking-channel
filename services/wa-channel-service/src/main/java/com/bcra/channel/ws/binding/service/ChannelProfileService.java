package com.bcra.channel.ws.binding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.binding.dto.ChannelProfileResponse;
import com.bcra.channel.ws.binding.dto.UpdatePreferencesRequest;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingNotFoundException;
import com.bcra.channel.ws.binding.mapper.BindingMapper;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.client.UserLookupStatus;
import com.bcra.channel.ws.client.UsersServiceClient;
import com.bcra.channel.ws.client.UsersServiceUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consulta del perfil del canal y actualización de preferencias sobre vínculos activos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelProfileService {

    private static final String ACTIVE_BINDING_REQUIRED =
            "No existe un vínculo activo para el número indicado";

    private final BindingRepository bindingRepository;
    private final BindingMapper bindingMapper;
    private final UsersServiceClient usersServiceClient;
    private final Clock clock;

    /**
     * Devuelve el perfil del canal para un vínculo activo.
     *
     * @param whatsappNumber número en E.164
     * @return perfil con {@code displayName} de H1
     * @throws BindingNotFoundException         si no hay vínculo {@link BindingStatus#ACTIVO}
     * @throws UsersServiceUnavailableException si H1 no responde o no entrega el nombre
     */
    @Transactional(readOnly = true)
    public ChannelProfileResponse getProfile(String whatsappNumber) {
        WhatsappNumberFormat.requireValid(whatsappNumber);
        Binding binding = requireActiveBinding(whatsappNumber);
        String displayName = resolveDisplayName(binding.getClientId());
        return bindingMapper.toProfileResponse(binding, displayName);
    }

    /**
     * Actualiza idioma y notificaciones de un vínculo activo.
     *
     * @param whatsappNumber número en E.164
     * @param request        preferencias validadas
     * @return perfil actualizado
     * @throws BindingNotFoundException         si no hay vínculo {@link BindingStatus#ACTIVO}
     * @throws UsersServiceUnavailableException si H1 no responde al armar el perfil
     */
    @Transactional
    public ChannelProfileResponse updatePreferences(String whatsappNumber, UpdatePreferencesRequest request) {
        Objects.requireNonNull(request, "request");
        WhatsappNumberFormat.requireValid(whatsappNumber);

        Binding binding = requireActiveBindingForUpdate(whatsappNumber);
        Instant now = Instant.now(clock);
        binding.setLanguagePreference(request.languagePreference());
        binding.setNotificationsEnabled(request.notificationsEnabled());
        binding.setUpdatedAt(now);

        Binding saved = bindingRepository.save(binding);
        String displayName = resolveDisplayName(saved.getClientId());
        log.info("Preferencias actualizadas: bindingId={}, language={}",
                saved.getId(), saved.getLanguagePreference());
        return bindingMapper.toProfileResponse(saved, displayName);
    }

    private Binding requireActiveBinding(String whatsappNumber) {
        return bindingRepository.findByWhatsappNumber(whatsappNumber)
                .filter(binding -> binding.getStatus() == BindingStatus.ACTIVO)
                .orElseThrow(() -> new BindingNotFoundException(ACTIVE_BINDING_REQUIRED));
    }

    private Binding requireActiveBindingForUpdate(String whatsappNumber) {
        return bindingRepository.findByWhatsappNumberForUpdate(whatsappNumber)
                .filter(binding -> binding.getStatus() == BindingStatus.ACTIVO)
                .orElseThrow(() -> new BindingNotFoundException(ACTIVE_BINDING_REQUIRED));
    }

    private String resolveDisplayName(UUID clientId) {
        if (clientId == null) {
            throw new UsersServiceUnavailableException("Active binding has no clientId");
        }
        UserLookupResult lookup = usersServiceClient.findById(clientId);
        if (lookup.status() == UserLookupStatus.NOT_FOUND || lookup.displayName() == null) {
            log.warn("Users service did not return displayName for clientId={}", clientId);
            throw new UsersServiceUnavailableException("Users service did not return a display name");
        }
        return lookup.displayName();
    }
}
