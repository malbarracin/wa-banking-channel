package com.bcra.channel.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.exception.AuditHistoryNotFoundException;
import com.bcra.channel.ws.audit.mapper.AuditMapperImpl;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.audit.service.AuditService;

@DataJpaTest
@Import({AuditService.class, AuditMapperImpl.class, AuditServiceHistoryDataJpaTest.FixedClockConfig.class})
class AuditServiceHistoryDataJpaTest {

    private static final String NUMBER = "+5491112345678";
    private static final String OTHER_NUMBER = "+5491187654321";
    private static final UUID BINDING_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    @DisplayName("El historial trae solo las entradas del número, de la más nueva a la más vieja")
    void historyReturnsOnlyEntriesOfNumberOrderedDesc() {
        save(NUMBER, AuditAction.TERMS_ACCEPTED, Instant.parse("2026-11-27T10:00:00Z"));
        save(NUMBER, AuditAction.BINDING_ACTIVATED, Instant.parse("2026-11-27T12:00:00Z"));
        save(NUMBER, AuditAction.IDENTITY_VERIFIED, Instant.parse("2026-11-27T11:00:00Z"));
        save(OTHER_NUMBER, AuditAction.BINDING_UNBOUND, Instant.parse("2026-11-27T13:00:00Z"));

        List<AuditEntryResponse> history = auditService.findHistoryByWhatsappNumber(NUMBER);

        assertThat(history)
                .extracting(AuditEntryResponse::action)
                .containsExactly(
                        AuditAction.BINDING_ACTIVATED,
                        AuditAction.IDENTITY_VERIFIED,
                        AuditAction.TERMS_ACCEPTED);
        assertThat(history).allSatisfy(entry -> assertThat(entry.whatsappNumber()).isEqualTo(NUMBER));
    }

    @Test
    @DisplayName("Un número sin entradas lanza AuditHistoryNotFoundException")
    void historyWithoutEntriesThrowsNotFound() {
        assertThatThrownBy(() -> auditService.findHistoryByWhatsappNumber(OTHER_NUMBER))
                .isInstanceOf(AuditHistoryNotFoundException.class);
    }

    @Test
    @DisplayName("record acepta detalle nulo")
    void recordAcceptsNullDetail() {
        AuditEntry saved = auditService.record(
                BINDING_ID, NUMBER, AuditAction.BINDING_UNBOUND, AuditService.RESULT_OK, "CLIENTE", null);

        assertThat(auditRepository.findById(saved.getId()).orElseThrow().getDetail()).isNull();
    }

    @Test
    @DisplayName("record rechaza campos obligatorios nulos sin persistir nada")
    void recordRejectsMissingMandatoryFields() {
        assertThatThrownBy(() -> auditService.record(
                null, NUMBER, AuditAction.TERMS_ACCEPTED, AuditService.RESULT_OK, "CLIENTE", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bindingId");
        assertThatThrownBy(() -> auditService.record(
                BINDING_ID, null, AuditAction.TERMS_ACCEPTED, AuditService.RESULT_OK, "CLIENTE", null))
                .hasMessage("whatsappNumber");
        assertThatThrownBy(() -> auditService.record(
                BINDING_ID, NUMBER, null, AuditService.RESULT_OK, "CLIENTE", null))
                .hasMessage("action");
        assertThatThrownBy(() -> auditService.record(
                BINDING_ID, NUMBER, AuditAction.TERMS_ACCEPTED, null, "CLIENTE", null))
                .hasMessage("result");
        assertThatThrownBy(() -> auditService.record(
                BINDING_ID, NUMBER, AuditAction.TERMS_ACCEPTED, AuditService.RESULT_OK, null, null))
                .hasMessage("actor");
        assertThatThrownBy(() -> auditService.findHistoryByWhatsappNumber(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(auditRepository.count()).isZero();
    }

    private void save(String number, AuditAction action, Instant occurredAt) {
        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setBindingId(BINDING_ID);
        entry.setWhatsappNumber(number);
        entry.setAction(action);
        entry.setResult(AuditService.RESULT_OK);
        entry.setActor("CLIENTE");
        entry.setOccurredAt(occurredAt);
        auditRepository.saveAndFlush(entry);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-27T15:30:00Z"), ZoneOffset.UTC);
        }
    }
}
