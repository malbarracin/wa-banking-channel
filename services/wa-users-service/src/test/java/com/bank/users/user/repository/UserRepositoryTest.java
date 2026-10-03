package com.bank.users.user.repository;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository — persistencia y unicidad de documento")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    @DisplayName("Persiste el usuario con id generado y timestamps, y lo recupera por id y documento")
    void savesAndFindsUser() {
        User saved = repository.saveAndFlush(user(DocumentType.DNI, "30123456", UserStatus.ACTIVE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(repository.findById(saved.getId())).contains(saved);
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30123456"))
                .hasValueSatisfying(u -> assertThat(u.getFirstName()).isEqualTo("Juan"));
        assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "30123456")).isTrue();
        assertThat(repository.existsByDocumentTypeAndDocumentNumber(DocumentType.DNI, "99999999")).isFalse();
        assertThat(repository.findByDocumentTypeAndDocumentNumber(DocumentType.CUIT, "30123456")).isEmpty();
    }

    @Test
    @DisplayName("La BD rechaza un documento repetido aunque el existente esté INACTIVE")
    void rejectsDuplicateDocumentEvenWhenInactive() {
        repository.saveAndFlush(user(DocumentType.DNI, "30123456", UserStatus.INACTIVE));

        assertThatThrownBy(() -> repository.saveAndFlush(user(DocumentType.DNI, "30123456", UserStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Igual número con distinto tipo no es duplicado")
    void sameNumberDifferentTypeIsAllowed() {
        repository.saveAndFlush(user(DocumentType.CUIT, "20301234567", UserStatus.ACTIVE));
        repository.saveAndFlush(user(DocumentType.CUIL, "20301234567", UserStatus.ACTIVE));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("updatedAt se renueva al modificar y createdAt permanece")
    void updateKeepsCreatedAt() {
        User saved = repository.saveAndFlush(user(DocumentType.DNI, "1", UserStatus.ACTIVE));
        var createdAt = saved.getCreatedAt();

        saved.setStatus(UserStatus.SUSPENDED);
        User updated = repository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(updated.toString()).doesNotContain("Juan", "1990");
    }

    private static User user(DocumentType type, String number, UserStatus status) {
        User user = new User();
        user.setDocumentType(type);
        user.setDocumentNumber(number);
        user.setFirstName("Juan");
        user.setLastName("Pérez");
        user.setBirthDate(LocalDate.of(1990, 5, 15));
        user.setStatus(status);
        return user;
    }
}
