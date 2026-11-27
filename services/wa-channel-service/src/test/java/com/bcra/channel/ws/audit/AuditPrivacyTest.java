package com.bcra.channel.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.service.AuditService;

class AuditPrivacyTest {

    private static final String SECRET_CODE = "DOC-998877";
    private static final String WHATSAPP = "+5491112345678";

    @Test
    @DisplayName("AuditEntry.toString no expone número, detalle ni secretos de verificación")
    void auditEntryToStringHidesPiiAndSecrets() {
        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        entry.setBindingId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"));
        entry.setWhatsappNumber(WHATSAPP);
        entry.setAction(AuditAction.IDENTITY_VERIFIED);
        entry.setResult(AuditService.RESULT_OK);
        entry.setActor("CLIENTE");
        entry.setDetail("Verificación OK");
        entry.setOccurredAt(Instant.parse("2026-11-27T15:30:00Z"));

        String text = entry.toString();

        assertThat(text).doesNotContain(WHATSAPP);
        assertThat(text).doesNotContain(SECRET_CODE);
        assertThat(text).doesNotContain("Verificación OK");
        assertThat(text).doesNotContain("detail");
        assertThat(text).contains("IDENTITY_VERIFIED");
        assertThat(text).contains(AuditService.RESULT_OK);
    }

    @Test
    @DisplayName("AuditEntryResponse.toString no expone número, detalle ni secretos de verificación")
    void auditEntryResponseToStringHidesPiiAndSecrets() {
        AuditEntryResponse response = new AuditEntryResponse(
                UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
                WHATSAPP,
                AuditAction.IDENTITY_FAILED,
                AuditService.RESULT_ERROR,
                "CLIENTE",
                "Intento fallido sin código " + SECRET_CODE,
                Instant.parse("2026-11-27T15:30:00Z"));

        String text = response.toString();

        assertThat(text).doesNotContain(WHATSAPP);
        assertThat(text).doesNotContain(SECRET_CODE);
        assertThat(text).doesNotContain("Intento fallido");
        assertThat(text).doesNotContain("detail=");
        assertThat(text).contains("IDENTITY_FAILED");
        assertThat(text).contains(AuditService.RESULT_ERROR);
    }
}
