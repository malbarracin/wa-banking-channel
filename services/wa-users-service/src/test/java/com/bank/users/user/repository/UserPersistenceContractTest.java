package com.bank.users.user.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UserMapper.class))
@ActiveProfiles("test")
@DisplayName("Contrato durable de identidad y mapeo")
class UserPersistenceContractTest {

    private static final int TIMEOUT_SECONDS = 10;

    @Autowired
    private UserRepository repository;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private UserMapper mapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Recarga todos los campos y mapea la proyección común en cada estado")
    void reloadsAndMapsAllFields(UserStatus status) {
        // Given
        User saved = repository.saveAndFlush(user("contract", status));
        Long id = saved.getId();
        Instant createdAt = saved.getCreatedAt();
        Instant updatedAt = saved.getUpdatedAt();
        entityManager.clear();

        // When
        User loaded = repository.findById(id).orElseThrow();
        UserResponse response = mapper.toResponse(loaded);

        // Then
        assertThat(id).isPositive();
        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isEqualTo(createdAt);
        assertThat(response).isEqualTo(new UserResponse(id, DocumentType.PASAPORTE, "contract",
                "Ana", "Gomez", "Ani", "ana@example.com", LocalDate.of(1992, 2, 29), status,
                loaded.getCreatedAt(), loaded.getUpdatedAt()));
        // SQL timestamps may have lower precision than Instant.
        assertThat(loaded.getCreatedAt()).isCloseTo(createdAt,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, "contract"))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(id));
        assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, "contract")).isTrue();
        assertThat(loaded.toString()).doesNotContain("contract", "Ana", "Gomez", "Ani", "ana@example.com", "1992");
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("La restricción de BD rechaza duplicados en todos los estados")
    void rejectsDuplicateInEveryStatus(UserStatus existingStatus) {
        // Given
        repository.saveAndFlush(user("duplicate", existingStatus));
        entityManager.clear();

        // When / Then
        assertThatThrownBy(() -> repository.saveAndFlush(user("duplicate", UserStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La actualización conserva createdAt tras volver a leer la BD")
    void reloadsUpdatedTimestamps() {
        // Given
        User saved = repository.saveAndFlush(user("update", UserStatus.ACTIVE));
        Long id = saved.getId();
        entityManager.clear();
        User loaded = repository.findById(id).orElseThrow();
        Instant createdAt = loaded.getCreatedAt();
        // Fixed old timestamp avoids timing assumptions about the execution speed.
        Instant oldUpdatedAt = Instant.parse("2000-01-01T00:00:00Z");
        entityManager.getEntityManager().createNativeQuery("update users set updated_at = :time where id = :id")
                .setParameter("time", oldUpdatedAt).setParameter("id", id).executeUpdate();
        entityManager.clear();

        // When
        loaded = repository.findById(id).orElseThrow();
        loaded.setDisplayName("Nuevo nombre");
        repository.flush();
        entityManager.clear();
        User updated = repository.findById(id).orElseThrow();

        // Then
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(oldUpdatedAt);
        assertThat(updated.getDisplayName()).isEqualTo("Nuevo nombre");
    }

    @Test
    @DisplayName("Las búsquedas ausentes no encuentran usuario ni mezclan tipos de documento")
    void missingIdentity() {
        repository.saveAndFlush(user("known", UserStatus.SUSPENDED));
        entityManager.clear();

        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, "missing")).isEmpty();
        assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, "missing")).isFalse();
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "known")).isEmpty();
        assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "known")).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Dos altas concurrentes que ven documento libre solo confirman un registro")
    void concurrentInsertsCommitOnlyOneUser() throws Exception {
        // Given: each worker owns its transaction; both check before either inserts.
        String number = "concurrent-contract";
        CyclicBarrier barrier = new CyclicBarrier(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> insert = () -> {
            try {
                return transaction.execute(status -> {
                    assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, number))
                            .isFalse();
                    awaitBothWorkers(barrier);
                    repository.saveAndFlush(user(number, UserStatus.ACTIVE));
                    return true;
                });
            } catch (DataIntegrityViolationException expectedDuplicate) {
                return false;
            }
        };

        try {
            // When
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            List<Boolean> outcomes = List.of(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Then: the loser must fail on uniqueness, not a timeout or other exception.
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            transaction.executeWithoutResult(status -> {
                assertThat(repository.findAll().stream()
                        .filter(u -> number.equals(u.getDocumentNumber()))).hasSize(1);
                assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, number))
                        .isPresent();
            });
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> repository
                    .findByDocumentTypeAndDocumentNumber(DocumentType.PASAPORTE, number)
                    .ifPresent(repository::delete));
        }
    }

    private static void awaitBothWorkers(CyclicBarrier barrier) {
        try {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent insert was interrupted", interrupted);
        } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException failure) {
            throw new AssertionError("Both transactions must check existence before inserting", failure);
        }
    }

    private User user(String number, UserStatus status) {
        User user = mapper.toEntity(new CreateUserRequest(DocumentType.PASAPORTE, number, "Ana", "Gomez",
                "Ani", "ana@example.com", LocalDate.of(1992, 2, 29)));
        assertThat(user.getId()).isNull();
        assertThat(user.getStatus()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
        user.setStatus(status);
        return user;
    }
}
