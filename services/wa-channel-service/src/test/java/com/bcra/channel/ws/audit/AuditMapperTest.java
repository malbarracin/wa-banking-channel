package com.bcra.channel.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.mapper.AuditMapper;
import com.bcra.channel.ws.audit.mapper.AuditMapperImpl;
import com.bcra.channel.ws.audit.service.AuditService;

class AuditMapperTest {

    private final AuditMapper auditMapper = new AuditMapperImpl();

    @Test
    @DisplayName("toResponse copia todos los campos de la entrada")
    void toResponseMapsAllFields() {
        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        entry.setBindingId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"));
        entry.setWhatsappNumber("+5491112345678");
        entry.setAction(AuditAction.BINDING_BLOCKED);
        entry.setResult(AuditService.RESULT_BLOQUEADO);
        entry.setActor("BANCO");
        entry.setDetail("Bloqueo por política");
        entry.setOccurredAt(Instant.parse("2026-11-27T15:30:00Z"));

        AuditEntryResponse response = auditMapper.toResponse(entry);

        assertThat(response.id()).isEqualTo(entry.getId());
        assertThat(response.bindingId()).isEqualTo(entry.getBindingId());
        assertThat(response.whatsappNumber()).isEqualTo("+5491112345678");
        assertThat(response.action()).isEqualTo(AuditAction.BINDING_BLOCKED);
        assertThat(response.result()).isEqualTo(AuditService.RESULT_BLOQUEADO);
        assertThat(response.actor()).isEqualTo("BANCO");
        assertThat(response.detail()).isEqualTo("Bloqueo por política");
        assertThat(response.occurredAt()).isEqualTo(entry.getOccurredAt());
    }

    @Test
    @DisplayName("toResponse con null devuelve null")
    void toResponseWithNullReturnsNull() {
        assertThat(auditMapper.toResponse(null)).isNull();
    }
}
