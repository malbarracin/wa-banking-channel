package com.bank.users.user.mapper;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMapper — mapeo común")
class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    @DisplayName("Request a entidad sin id, estado ni timestamps; entidad a respuesta completa")
    void mapsRequestAndResponse() {
        var request = new CreateUserRequest(DocumentType.DNI, "30123456", "Juan", "Pérez",
                "Juancho", "juan@example.com", LocalDate.of(1990, 5, 15));

        User user = mapper.toEntity(request);
        assertThat(user.getId()).isNull();
        assertThat(user.getStatus()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getDocumentNumber()).isEqualTo("30123456");
        assertThat(user.getEmail()).isEqualTo("juan@example.com");

        user.setStatus(UserStatus.ACTIVE);
        UserResponse response = mapper.toResponse(user);
        assertThat(response.documentType()).isEqualTo(DocumentType.DNI);
        assertThat(response.firstName()).isEqualTo("Juan");
        assertThat(response.lastName()).isEqualTo("Pérez");
        assertThat(response.displayName()).isEqualTo("Juancho");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Null produce null")
    void mapsNulls() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
    }
}
