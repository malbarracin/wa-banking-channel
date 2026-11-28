package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
import com.bcra.channel.ws.binding.dto.ChannelProfileResponse;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.mapper.BindingMapper;
import com.bcra.channel.ws.binding.mapper.BindingMapperImpl;

class BindingMapperTest {

    private static final String NUMBER = "+5491112345678";

    private final BindingMapper mapper = new BindingMapperImpl();

    @Test
    @DisplayName("toStatusResponse mapea id a bindingId junto con status y clientId")
    void toStatusResponseMapsFields() {
        Binding binding = new Binding();
        binding.setId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"));
        binding.setWhatsappNumber(NUMBER);
        binding.setClientId(UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab"));
        binding.setStatus(BindingStatus.ACTIVO);

        BindingStatusResponse response = mapper.toStatusResponse(binding);

        assertThat(response.whatsappNumber()).isEqualTo(NUMBER);
        assertThat(response.status()).isEqualTo(BindingStatus.ACTIVO);
        assertThat(response.bindingId()).isEqualTo(binding.getId());
        assertThat(response.clientId()).isEqualTo(binding.getClientId());
    }

    @Test
    @DisplayName("toStatusResponse de null devuelve null")
    void toStatusResponseOfNullIsNull() {
        assertThat(mapper.toStatusResponse(null)).isNull();
    }

    @Test
    @DisplayName("unboundStatus devuelve SIN_VINCULO sin clientId ni bindingId")
    void unboundStatusHasNoIds() {
        BindingStatusResponse response = mapper.unboundStatus(NUMBER);

        assertThat(response.whatsappNumber()).isEqualTo(NUMBER);
        assertThat(response.status()).isEqualTo(BindingStatus.SIN_VINCULO);
        assertThat(response.clientId()).isNull();
        assertThat(response.bindingId()).isNull();
    }

    @Test
    @DisplayName("toProfileResponse mapea vínculo y displayName al perfil del canal")
    void toProfileResponseMapsBindingAndDisplayName() {
        Instant created = Instant.parse("2026-11-27T12:00:00Z");
        Instant updated = Instant.parse("2026-11-27T12:30:00Z");
        Binding binding = new Binding();
        binding.setId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"));
        binding.setWhatsappNumber(NUMBER);
        binding.setClientId(UUID.fromString("c1d2e3f4-a5b6-7890-cdef-1234567890ab"));
        binding.setStatus(BindingStatus.ACTIVO);
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setCreatedAt(created);
        binding.setUpdatedAt(updated);

        ChannelProfileResponse response = mapper.toProfileResponse(binding, "Ana Pérez");

        assertThat(response.whatsappNumber()).isEqualTo(NUMBER);
        assertThat(response.clientId()).isEqualTo(binding.getClientId());
        assertThat(response.displayName()).isEqualTo("Ana Pérez");
        assertThat(response.bindingStatus()).isEqualTo(BindingStatus.ACTIVO);
        assertThat(response.languagePreference()).isEqualTo("es");
        assertThat(response.notificationsEnabled()).isTrue();
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.updatedAt()).isEqualTo(updated);
    }
}
