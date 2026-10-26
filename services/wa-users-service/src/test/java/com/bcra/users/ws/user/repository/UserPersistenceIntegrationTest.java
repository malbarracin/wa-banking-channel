package com.bcra.users.ws.user.repository;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class UserPersistenceIntegrationTest {

    private final UserRepository repository;
    private final EntityManager entityManager;

    @Autowired
    UserPersistenceIntegrationTest(UserRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NULL,12345678,documento.tipo",
        "'',12345678,documento.tipo",
        "'   ',12345678,documento.tipo",
        "DNI,NULL,documento.numero",
        "DNI,'',documento.numero",
        "DNI,'   ',documento.numero"
    }, nullValues = "NULL")
    @DisplayName("Rechaza cada componente nulo, vacío o en blanco del documento")
    void rechazaComponenteInvalidoDelDocumento(String tipo, String numero, String campo) {
        User usuario = crearUsuario();
        usuario.getDocumento().setTipo(tipo);
        usuario.getDocumento().setNumero(numero);

        assertThatThrownBy(() -> repository.saveAndFlush(usuario))
                .isInstanceOfSatisfying(ConstraintViolationException.class, excepcion ->
                        assertThat(excepcion.getConstraintViolations())
                                .extracting(violacion -> violacion.getPropertyPath().toString())
                                .containsExactly(campo));
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Encuentra por documento al usuario en cualquier estado operativo")
    void consultaDocumentoSinFiltrarEstado(UserStatus estado) {
        User usuario = crearUsuario();
        usuario.setEstado(estado);
        repository.saveAndFlush(usuario);
        entityManager.clear();

        assertThat(repository.findByDocumentoTipoAndDocumentoNumero("DNI", "12345678"))
                .hasValueSatisfying(encontrado -> {
                    assertThat(encontrado.getId()).isEqualTo(usuario.getId());
                    assertThat(encontrado.getEstado()).isEqualTo(estado);
                });
    }

    @Test
    @DisplayName("Persiste los campos opcionales nulos y conserva el teléfono como contacto")
    void persisteCamposOpcionalesNulos() {
        User usuario = repository.saveAndFlush(crearUsuario());
        entityManager.clear();

        User recargado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(recargado.getEmail()).isNull();
        assertThat(recargado.getNombreTratamiento()).isNull();
        assertThat(recargado.getPreferencias()).isNull();
        assertThat(recargado.getTelefono()).isEqualTo("555123456");
    }

    private User crearUsuario() {
        Documento documento = new Documento();
        documento.setTipo("DNI");
        documento.setNumero("12345678");
        User usuario = new User();
        usuario.setDocumento(documento);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        usuario.setTelefono("555123456");
        return usuario;
    }
}
