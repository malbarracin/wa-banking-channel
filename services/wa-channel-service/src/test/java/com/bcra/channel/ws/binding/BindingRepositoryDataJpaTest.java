package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.entity.VerificationAttempt;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;

@DataJpaTest
class BindingRepositoryDataJpaTest {

    private static final String NUMBER = "+5491112345678";
    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private VerificationAttemptRepository verificationAttemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Las migraciones V1 y V2 quedan aplicadas por Flyway")
    void migrationsV1AndV2AreApplied() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\""
                        + " WHERE \"version\" IN ('1', '2') AND \"success\" = TRUE",
                Integer.class);

        assertThat(applied).isEqualTo(2);
    }

    @Test
    @DisplayName("El índice único en whatsapp_number rechaza un segundo vínculo del mismo número")
    void uniqueIndexRejectsDuplicateNumber() {
        bindingRepository.saveAndFlush(newBinding(UUID.randomUUID(), BindingStatus.ACTIVO));

        Binding duplicate = newBinding(UUID.randomUUID(), BindingStatus.PENDIENTE_VERIFICACION);

        assertThatThrownBy(() -> bindingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByWhatsappNumberForUpdate devuelve el vínculo existente y vacío si no hay registro")
    void findForUpdateReturnsBindingOrEmpty() {
        UUID id = UUID.randomUUID();
        bindingRepository.saveAndFlush(newBinding(id, BindingStatus.BLOQUEADO));

        assertThat(bindingRepository.findByWhatsappNumberForUpdate(NUMBER))
                .get()
                .extracting(Binding::getId, Binding::getStatus)
                .containsExactly(id, BindingStatus.BLOQUEADO);
        assertThat(bindingRepository.findByWhatsappNumberForUpdate("+5491100000000")).isEmpty();
    }

    @Test
    @DisplayName("Los valores por defecto de idioma y notificaciones se persisten")
    void defaultPreferencesArePersisted() {
        Binding binding = new Binding();
        binding.setId(UUID.randomUUID());
        binding.setWhatsappNumber(NUMBER);
        binding.setStatus(BindingStatus.PENDIENTE_VERIFICACION);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(NOW);
        binding.setUpdatedAt(NOW);
        bindingRepository.saveAndFlush(binding);

        Binding fromDb = bindingRepository.findByWhatsappNumber(NUMBER).orElseThrow();
        assertThat(fromDb.getLanguagePreference()).isEqualTo("es");
        assertThat(fromDb.isNotificationsEnabled()).isTrue();
        assertThat(fromDb.getClientId()).isNull();
    }

    @Test
    @DisplayName("Un intento de verificación se guarda asociado a un vínculo existente")
    void verificationAttemptIsPersistedForBinding() {
        UUID bindingId = UUID.randomUUID();
        bindingRepository.saveAndFlush(newBinding(bindingId, BindingStatus.PENDIENTE_VERIFICACION));

        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setBindingId(bindingId);
        attempt.setAttemptedAt(NOW);
        attempt.setSuccess(false);
        verificationAttemptRepository.saveAndFlush(attempt);

        assertThat(verificationAttemptRepository.findById(attempt.getId()))
                .get()
                .extracting(VerificationAttempt::getBindingId, VerificationAttempt::isSuccess)
                .containsExactly(bindingId, false);
    }

    @Test
    @DisplayName("La FK de verification_attempts rechaza un intento sin vínculo")
    void verificationAttemptWithoutBindingIsRejected() {
        VerificationAttempt orphan = new VerificationAttempt();
        orphan.setId(UUID.randomUUID());
        orphan.setBindingId(UUID.randomUUID());
        orphan.setAttemptedAt(NOW);
        orphan.setSuccess(true);

        assertThatThrownBy(() -> verificationAttemptRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("toString del vínculo no expone el número WhatsApp")
    void toStringDoesNotExposeNumber() {
        Binding binding = newBinding(UUID.randomUUID(), BindingStatus.ACTIVO);

        assertThat(binding.toString()).doesNotContain(NUMBER).contains("ACTIVO");
    }

    private static Binding newBinding(UUID id, BindingStatus status) {
        Binding binding = new Binding();
        binding.setId(id);
        binding.setWhatsappNumber(NUMBER);
        binding.setStatus(status);
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor("CLIENTE");
        binding.setCreatedAt(NOW);
        binding.setUpdatedAt(NOW);
        return binding;
    }
}
