package com.bcra.ws.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.ws.users.api.v1.dto.UserCreateRequest;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.api.v1.mapper.UserMapper;
import com.bcra.ws.users.domain.AuditAction;
import com.bcra.ws.users.domain.AuditResult;
import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;
import com.bcra.ws.users.entity.UserEntity;
import com.bcra.ws.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserRegistrationMappingTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuditService userAuditService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "en"})
    @DisplayName("Should preserve compliance data through registration with the generated mapper")
    void shouldPreserveComplianceDataThroughRegistration(String preferredLanguage) {
        UserCreateRequest request = UserCreateRequest.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("30111222")
                .firstName("Maria")
                .lastName("Lopez")
                .email("maria.lopez@example.com")
                .phone("+54911111111")
                .address("Av. Corrientes 1234")
                .birthDate(LocalDate.of(1990, 5, 20))
                .preferredLanguage(preferredLanguage)
                .build();
        String expectedLanguage = "en".equals(preferredLanguage) ? "en" : "es";
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            assertThat(entity.getAddress()).isEqualTo(request.getAddress());
            assertThat(entity.getBirthDate()).isEqualTo(request.getBirthDate());
            assertThat(entity.getPhone()).isEqualTo(request.getPhone());
            assertThat(entity.getPreferredName()).isNull();
            assertThat(entity.getPreferredLanguage()).isEqualTo(expectedLanguage);
            assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(entity.getCreatedAt()).isNotNull().isEqualTo(entity.getUpdatedAt());
            entity.setId("user-1");
            return entity;
        });
        UserServiceImpl service = new UserServiceImpl(
                userRepository, Mappers.getMapper(UserMapper.class), userAuditService);

        UserResponse response = service.create(request, "tester");

        assertThat(response.getId()).isEqualTo("user-1");
        assertThat(response.getAddress()).isEqualTo(request.getAddress());
        assertThat(response.getBirthDate()).isEqualTo(request.getBirthDate());
        assertThat(response.getPhone()).isEqualTo(request.getPhone());
        assertThat(response.getPreferredName()).isNull();
        assertThat(response.getPreferredLanguage()).isEqualTo(expectedLanguage);
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getCreatedAt()).isNotNull().isEqualTo(response.getUpdatedAt());
        verify(userAuditService).record("user-1", AuditAction.USER_CREATED,
                AuditResult.SUCCESS, Map.of("status", "ACTIVE"), "tester");
    }
}
