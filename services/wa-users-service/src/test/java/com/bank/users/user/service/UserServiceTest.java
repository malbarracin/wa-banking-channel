package com.bank.users.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bank.users.exception.UserNotFoundException;
import com.bank.users.user.dto.EligibilityResponse;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

class UserServiceTest {

    private static final Long ID = 7L;
    private static final String NUMBER = "30123456";

    private UserRepository repository;
    private UserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        mapper = mock(UserMapper.class);
        service = new UserService(repository, mapper);
    }

    @Test
    @DisplayName("findById devuelve la proyección del usuario existente sin modificarlo")
    void findByIdReturnsResponse() {
        User user = user(UserStatus.SUSPENDED);
        UserResponse response = response(UserStatus.SUSPENDED);
        when(repository.findById(ID)).thenReturn(Optional.of(user));
        when(mapper.toResponse(user)).thenReturn(response);

        assertThat(service.findById(ID)).isSameAs(response);
        verify(repository).findById(ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("findById de un id inexistente lanza UserNotFoundException")
    void findByIdNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(ID)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("findByDocument devuelve la proyección del usuario con ese documento compuesto")
    void findByDocumentReturnsResponse() {
        User user = user(UserStatus.INACTIVE);
        UserResponse response = response(UserStatus.INACTIVE);
        when(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, NUMBER)).thenReturn(Optional.of(user));
        when(mapper.toResponse(user)).thenReturn(response);

        assertThat(service.findByDocument(DocumentType.DNI, NUMBER)).isSameAs(response);
    }

    @Test
    @DisplayName("findByDocument inexistente lanza UserNotFoundException sin exponer el documento")
    void findByDocumentNotFound() {
        when(repository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByDocument(DocumentType.DNI, NUMBER))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageNotContaining(NUMBER);
    }

    @ParameterizedTest(name = "{0} -> eligible={1}")
    @CsvSource({"ACTIVE,true", "SUSPENDED,false", "INACTIVE,false"})
    @DisplayName("checkEligibility es true únicamente para ACTIVE")
    void checkEligibility(UserStatus status, boolean expected) {
        when(repository.findById(ID)).thenReturn(Optional.of(user(status)));

        assertThat(service.checkEligibility(ID)).isEqualTo(new EligibilityResponse(ID, expected));
    }

    @Test
    @DisplayName("checkEligibility de un id inexistente lanza UserNotFoundException")
    void checkEligibilityNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkEligibility(ID)).isInstanceOf(UserNotFoundException.class);
    }

    private static User user(UserStatus status) {
        User user = new User();
        user.setId(ID);
        user.setDocumentType(DocumentType.DNI);
        user.setDocumentNumber(NUMBER);
        user.setStatus(status);
        return user;
    }

    private static UserResponse response(UserStatus status) {
        return new UserResponse(ID, DocumentType.DNI, NUMBER, "Juan", "Perez", null, null, null, status, null, null);
    }
}
