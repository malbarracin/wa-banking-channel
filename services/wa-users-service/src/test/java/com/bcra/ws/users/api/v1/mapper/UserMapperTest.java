package com.bcra.ws.users.api.v1.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.bcra.ws.users.api.v1.dto.UserCreateRequest;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserAuditEventEntity;
import com.bcra.ws.users.entity.UserEntity;

/**
 * Verifies the generated API v1 mapper independently of MongoDB.
 */
class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    @DisplayName("Should preserve registration data without assigning service-owned fields")
    void shouldPreserveRegistrationData() {
        UserCreateRequest request = UserCreateRequest.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .phone("+5491112345678")
                .address("Av. Corrientes 1234")
                .birthDate(LocalDate.of(1990, 5, 20))
                .preferredName("Mari")
                .preferredLanguage("es")
                .build();

        UserEntity entity = mapper.toEntity(request);

        assertThat(entity).usingRecursiveComparison()
                .ignoringFields("id", "status", "createdAt", "updatedAt")
                .isEqualTo(request);
        assertThat(entity.getId()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Should expose user data and allow channel linking only for ACTIVE users")
    void shouldPreserveUserDataAndCalculateChannelEligibility() {
        Instant timestamp = Instant.parse("2026-10-19T12:00:00Z");
        UserEntity entity = UserEntity.builder()
                .id("user-1")
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .phone("+5491112345678")
                .address("Av. Corrientes 1234")
                .birthDate(LocalDate.of(1990, 5, 20))
                .preferredName("Mari")
                .preferredLanguage("es")
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();

        for (UserStatus status : UserStatus.values()) {
            entity.setStatus(status);
            UserResponse response = mapper.toResponse(entity);

            assertThat(response).usingRecursiveComparison()
                    .ignoringFields("channelLinkEligible").isEqualTo(entity);
            assertThat(response.isChannelLinkEligible()).isEqualTo(status == UserStatus.ACTIVE);
        }
        assertThat(mapper.toChannelLinkEligible(null)).isFalse();
    }

    @Test
    @DisplayName("Should preserve audit event data and history order")
    void shouldPreserveAuditHistory() {
        UserAuditEventEntity created = UserAuditEventEntity.builder()
                .id("audit-1")
                .userId("user-1")
                .action(AuditAction.USER_CREATED)
                .timestamp(Instant.parse("2026-10-19T12:00:00Z"))
                .result(AuditResult.SUCCESS)
                .summary(Map.of("status", "ACTIVE"))
                .actor("SYSTEM")
                .build();
        UserAuditEventEntity changed = UserAuditEventEntity.builder()
                .id("audit-2")
                .userId("user-1")
                .action(AuditAction.STATUS_CHANGED)
                .timestamp(Instant.parse("2026-10-19T13:00:00Z"))
                .result(AuditResult.FAILURE)
                .summary(Map.of())
                .actor("ops")
                .build();

        assertThat(mapper.toAuditResponse(created)).usingRecursiveComparison().isEqualTo(created);
        assertThat(mapper.toAuditResponseList(List.of(changed, created)))
                .usingRecursiveComparison().isEqualTo(List.of(changed, created));
        assertThat(mapper.toAuditResponseList(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Should handle absent mapping inputs without creating partial objects")
    void shouldHandleAbsentInputs() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toAuditResponse(null)).isNull();
        assertThat(mapper.toAuditResponseList(null)).isNull();
    }
}
