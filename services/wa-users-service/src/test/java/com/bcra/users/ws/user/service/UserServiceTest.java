package com.bcra.users.ws.user.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import com.bcra.users.ws.user.dto.CreateUserRequest;
import com.bcra.users.ws.user.dto.DocumentoDto;
import com.bcra.users.ws.user.entity.AuditAction;
import com.bcra.users.ws.user.entity.AuditResult;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.exception.DuplicateUserException;
import com.bcra.users.ws.user.mapper.UserMapper;
import com.bcra.users.ws.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Traduce y audita el duplicado detectado al guardar un alta concurrente")
    void rechazaDuplicadoConcurrente() {
        CreateUserRequest solicitud = solicitudValida();
        when(userRepository.findByDocumentoTipoAndDocumentoNumero("DNI", "123"))
                .thenReturn(Optional.empty(), Optional.of(new User()));
        when(userMapper.toEntity(solicitud)).thenReturn(new User());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("restricción"));

        assertThatThrownBy(() -> userService.alta(solicitud)).isInstanceOf(DuplicateUserException.class);

        verify(auditService).registrar(AuditAction.ALTA, AuditResult.RECHAZADO,
                "DNI", "123", "Documento duplicado");
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("Conserva otros errores de persistencia sin auditarlos como alta exitosa o duplicada")
    void conservaErrorDeIntegridadAjenoAlDocumento() {
        CreateUserRequest solicitud = solicitudValida();
        when(userRepository.findByDocumentoTipoAndDocumentoNumero("DNI", "123")).thenReturn(Optional.empty());
        when(userMapper.toEntity(solicitud)).thenReturn(new User());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("otra restricción"));

        assertThatThrownBy(() -> userService.alta(solicitud)).isInstanceOf(DataIntegrityViolationException.class);

        verify(auditService, never()).registrar(any(), any(), any(), any(), any());
    }

    private CreateUserRequest solicitudValida() {
        return new CreateUserRequest(new DocumentoDto("DNI", "123"), "Ana", "Pérez",
                LocalDate.of(1990, 1, 1), "contacto", null);
    }
}
