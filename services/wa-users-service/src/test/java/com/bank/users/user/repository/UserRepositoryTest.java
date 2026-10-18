package com.bank.users.user.repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserRepositoryTest {

    private final UserRepository repository;
    private final EntityManager entityManager;

    UserRepositoryTest(UserRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Recupera por ID y documento los estados y preferencias persistidos sin filtrar bajas")
    void retrievesPersistedUserByBothIdentifiers(UserStatus status) {
        User user = new User();
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber("12345678");
        user.setDisplayName("Ana");
        user.setBirthDate(LocalDate.of(1990, 1, 2));
        user.setStatus(status);
        user.setPreferences(Map.of("language", "es"));
        repository.saveAndFlush(user);
        UUID id = user.getId();
        entityManager.clear();

        User byId = repository.findById(id).orElseThrow();
        entityManager.clear();
        User byDocument = repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678")
                .orElseThrow();

        assertThat(byDocument.getId()).isEqualTo(byId.getId());
        assertThat(byDocument.getStatus()).isEqualTo(status);
        assertThat(byDocument.getPreferences()).containsEntry("language", "es");
        assertThat(byDocument.isChannelLinkEligible()).isEqualTo(status == UserStatus.ACTIVE);
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.PASSPORT, "12345678")).isEmpty();
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "99999999")).isEmpty();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }
}
