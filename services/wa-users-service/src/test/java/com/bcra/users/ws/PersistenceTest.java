package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.support.GatewayJwtTestConfig;
import com.bcra.users.ws.support.GatewayJwtTokens;
import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditEventRepository;
import com.bcra.users.ws.audit.service.AuditService;
import com.bcra.users.ws.user.entity.DocumentType;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;
import com.bcra.users.ws.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(GatewayJwtTestConfig.class)
@Transactional
class PersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private AuditEventRepository events;
    @Autowired private AuditService audit;

    private User user() {
        User user = new User();
        user.setTipoDocumento(DocumentType.DNI);
        user.setNumeroDocumento("12345678");
        user.setNombreCompleto("Ejemplo");
        user.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        user.setEmail("test@example.invalid");
        user.setTelefono("00000000");
        user.setEstado(UserStatus.ACTIVO);
        return user;
    }

    @Test
    void migrationEnforcesUniqueDocument() {
        users.saveAndFlush(user());
        assertThat(users.existsByTipoDocumentoAndNumeroDocumento(DocumentType.DNI, "12345678")).isTrue();
        assertThat(users.findByTipoDocumentoAndNumeroDocumento(DocumentType.DNI, "12345678")).isPresent();
        assertThatThrownBy(() -> users.saveAndFlush(user())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditRecordsOnlyFieldNames() {
        User saved = users.saveAndFlush(user());
        audit.record(saved.getId(), AuditAction.ALTA, AuditResult.EXITOSO, List.of("email"), "corr-1", "actor");
        var event = events.findByUserIdOrderByCreatedAtDesc(saved.getId()).getFirst();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getAction()).isEqualTo(AuditAction.ALTA);
        assertThat(event.getResultado()).isEqualTo(AuditResult.EXITOSO);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getDetalle()).containsExactly("email");
        assertThatThrownBy(() -> audit.record(saved.getId(), AuditAction.ALTA, AuditResult.RECHAZADO,
                List.of("test@example.invalid"), "corr-1", "actor")).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(AuditService.class.getDeclaredMethods())).noneMatch(method ->
                method.getName().startsWith("delete") || method.getName().startsWith("update"));
    }

    @Test
    void userStringDoesNotExposePersonalData() {
        assertThat(user().toString()).doesNotContain("12345678", "test@example.invalid", "00000000", "Ejemplo");
    }
}
