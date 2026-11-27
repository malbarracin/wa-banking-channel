package com.bcra.channel.ws.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.mapper.AuditMapperImpl;
import com.bcra.channel.ws.audit.repository.AuditRepository;
import com.bcra.channel.ws.audit.service.AuditService;

@DataJpaTest
@Import({AuditService.class, AuditMapperImpl.class, AuditServiceDataJpaTest.FixedClockConfig.class})
class AuditServiceDataJpaTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-27T15:30:00Z");

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    @DisplayName("record persiste action, result, actor y fecha")
    void recordPersistsEntry() {
        UUID bindingId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

        AuditEntry saved = auditService.record(
                bindingId,
                "+5491112345678",
                AuditAction.TERMS_ACCEPTED,
                AuditService.RESULT_OK,
                "CLIENTE",
                "Términos aceptados");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAction()).isEqualTo(AuditAction.TERMS_ACCEPTED);
        assertThat(saved.getResult()).isEqualTo(AuditService.RESULT_OK);
        assertThat(saved.getActor()).isEqualTo("CLIENTE");
        assertThat(saved.getOccurredAt()).isEqualTo(FIXED_INSTANT);

        AuditEntry fromDb = auditRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getAction()).isEqualTo(AuditAction.TERMS_ACCEPTED);
        assertThat(fromDb.getResult()).isEqualTo(AuditService.RESULT_OK);
        assertThat(fromDb.getActor()).isEqualTo("CLIENTE");
        assertThat(fromDb.getOccurredAt()).isEqualTo(FIXED_INSTANT);
        assertThat(fromDb.getDetail()).isEqualTo("Términos aceptados");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
