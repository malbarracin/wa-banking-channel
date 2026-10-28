package com.bank.users.user.service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.bank.users.audit.service.AuditService;
import com.bank.users.audit.repository.AuditLogRepository;
import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.exception.DuplicateUserException;
import com.bank.users.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ExtendWith(OutputCaptureExtension.class)
class CreateUserTransactionIntegrationTest {

    private final UserService service;
    private final UserRepository repository;
    private final AuditLogRepository auditRepository;

    @MockitoSpyBean
    private AuditService auditService;

    @MockitoSpyBean
    private UserRepository repositorySpy;

    CreateUserTransactionIntegrationTest(UserService service, UserRepository repository,
            AuditLogRepository auditRepository) {
        this.service = service;
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    @BeforeEach
    void clearRows() {
        auditRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    @DisplayName("Una auditoría fallida revierte el usuario ya insertado y el registro de auditoría")
    void rollsBackUserWhenAuditFails() {
        AuditService auditTarget = AopTestUtils.getUltimateTargetObject(auditService);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            auditRepository.flush();
            throw new IllegalStateException("audit unavailable");
        }).when(auditTarget).recordUserCreated(any());

        assertThatThrownBy(() -> service.create(request("12345678")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    @DisplayName("Dos altas concurrentes del mismo documento dejan un usuario y una auditoría")
    void commitsOnlyOneConcurrentCreation(CapturedOutput output) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return false;
        }).when(repositorySpy).existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678");
        Callable<String> create = () -> {
            try {
                service.create(request("12345678"));
                return "CREATED";
            } catch (DuplicateUserException exception) {
                return "CONFLICT";
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(create, create), 20, TimeUnit.SECONDS);
            assertThat(List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder("CREATED", "CONFLICT");
        }

        assertThat(repository.count()).isEqualTo(1);
        assertThat(auditRepository.count()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("12345678", "ana@example.com");
    }

    @Test
    @DisplayName("El mismo número puede pertenecer a tipos de documento distintos")
    void permitsSameNumberForDifferentTypes() {
        service.create(request("12345678"));
        service.create(new CreateUserRequest("PASSPORT", "12345678", "Ana", "ana@example.com", null,
                LocalDate.of(1990, 1, 2)));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(auditRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("La validación del servicio también rechaza un alta sin contacto")
    void validatesDirectServiceCalls() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("DNI", "12345678", "Ana", null, null,
                LocalDate.of(1990, 1, 2)))).isInstanceOf(jakarta.validation.ConstraintViolationException.class);

        assertThat(repository.count()).isZero();
    }

    private CreateUserRequest request(String documentNumber) {
        return new CreateUserRequest("DNI", documentNumber, "Ana", "ana@example.com", null,
                LocalDate.of(1990, 1, 2));
    }
}
