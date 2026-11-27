package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.binding.service.WhatsappNumberFormat;
import com.bcra.channel.ws.exception.BadRequestException;

class WhatsappNumberFormatTest {

    @Test
    @DisplayName("Número E.164 válido no lanza")
    void validNumberPasses() {
        assertThatCode(() -> WhatsappNumberFormat.requireValid("+5491112345678"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Número sin + o vacío lanza BadRequestException")
    void invalidNumberThrows() {
        assertThatThrownBy(() -> WhatsappNumberFormat.requireValid("5491112345678"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("formato inválido");
        assertThatThrownBy(() -> WhatsappNumberFormat.requireValid(null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> WhatsappNumberFormat.requireValid("+0"))
                .isInstanceOf(BadRequestException.class);
    }
}
