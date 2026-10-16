package com.bank.users.user.repository;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryBoundaryTest {

    private final UserRepository repository;
    private final TestEntityManager entityManager;

    @Autowired
    UserRepositoryBoundaryTest(UserRepository repository, TestEntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipoDocumento", "numeroDocumento", "nombreTratamiento", "status"})
    @DisplayName("Rechaza la ausencia de cada dato obligatorio al persistir")
    void rechazaDatoObligatorioAusente(String campo) {
        User usuario = nuevoUsuario();
        // Se introduce null deliberadamente para probar las restricciones de persistencia.
        switch (campo) {
            case "tipoDocumento" -> usuario.setTipoDocumento(null);
            case "numeroDocumento" -> usuario.setNumeroDocumento(null);
            case "nombreTratamiento" -> usuario.setNombreTratamiento(null);
            case "status" -> usuario.setStatus(null);
            default -> throw new IllegalArgumentException("Campo de prueba desconocido");
        }

        assertThatThrownBy(() -> repository.saveAndFlush(usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La baja lógica conserva la unicidad del documento")
    void conservaDocumentoTrasBajaLogica() {
        User usuario = nuevoUsuario();
        usuario.setStatus(UserStatus.BAJA_LOGICA);
        repository.saveAndFlush(usuario);
        entityManager.clear();

        assertThat(repository.existsByTipoDocumentoAndNumeroDocumento("DNI", "12345678")).isTrue();
        assertThatThrownBy(() -> repository.saveAndFlush(nuevoUsuario()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Actualiza y vacía preferencias JSON sin modificar la fecha de creación")
    void actualizaYVaciaPreferencias() {
        User usuario = repository.saveAndFlush(nuevoUsuario());
        var creacion = usuario.getCreatedAt();
        entityManager.clear();
        User recuperado = repository.findById(usuario.getId()).orElseThrow();
        Map<String, Object> preferencias = Map.of("canales", List.of("email", "telefono"),
                "presentacion", Map.of("tema", "claro"));

        recuperado.setPreferencias(preferencias);
        repository.flush();
        entityManager.clear();
        User actualizado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(actualizado.getPreferencias()).isEqualTo(preferencias);
        assertThat(actualizado.getCreatedAt()).isEqualTo(creacion);
        actualizado.setPreferencias(Map.of());
        repository.flush();
        entityManager.clear();

        assertThat(repository.findById(usuario.getId()).orElseThrow().getPreferencias()).isEmpty();
    }

    private User nuevoUsuario() {
        User usuario = new User();
        usuario.setTipoDocumento("DNI");
        usuario.setNumeroDocumento("12345678");
        usuario.setNombreTratamiento("Cliente");
        return usuario;
    }
}
