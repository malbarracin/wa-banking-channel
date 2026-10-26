package com.bcra.users.ws.user.repository;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.users.ws.user.entity.Documento;
import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    private final UserRepository repository;
    private final EntityManager entityManager;

    @Autowired
    UserRepositoryIntegrationTest(UserRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Rechaza documentos duplicados cualquiera sea el estado existente")
    void rechazaDocumentoDuplicadoEnCualquierEstado(UserStatus estado) {
        User existente = crearUsuario("DNI", "12345678");
        existente.setEstado(estado);
        repository.saveAndFlush(existente);

        assertThatThrownBy(() -> repository.saveAndFlush(crearUsuario("DNI", "12345678")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Consulta por ambos componentes del documento y conserva todos los datos")
    void encuentraUsuarioPorTipoYNumero() {
        User usuario = crearUsuario("TIPO_ABIERTO", "12345678");
        usuario.setNombreTratamiento("Ana");
        usuario.setPreferencias("idioma: español");
        repository.saveAndFlush(usuario);
        repository.saveAndFlush(crearUsuario("OTRO", "12345678"));
        repository.saveAndFlush(crearUsuario("TIPO_ABIERTO", "87654321"));
        entityManager.clear();

        User encontrado = repository.findByDocumentoTipoAndDocumentoNumero("TIPO_ABIERTO", "12345678")
                .orElseThrow();

        assertThat(encontrado.getId()).isEqualTo(usuario.getId());
        assertThat(encontrado.getDocumento()).isEqualTo(usuario.getDocumento());
        assertThat(encontrado.getNombre()).isEqualTo("Ana");
        assertThat(encontrado.getApellido()).isEqualTo("Pérez");
        assertThat(encontrado.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(encontrado.getEmail()).isEqualTo("ana@example.test");
        assertThat(encontrado.getTelefono()).isEqualTo("555123456");
        assertThat(encontrado.getNombreTratamiento()).isEqualTo("Ana");
        assertThat(encontrado.getPreferencias()).isEqualTo("idioma: español");
        assertThat(encontrado.getEstado()).isEqualTo(UserStatus.ACTIVE);
        assertThat(encontrado.getCreatedAt()).isNotNull();
        assertThat(encontrado.getUpdatedAt()).isNotNull();
        assertThat(repository.findByDocumentoTipoAndDocumentoNumero("INEXISTENTE", "12345678")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Persiste cada estado por su nombre y permite consultarlo tras recargar")
    void persisteEstadoComoTexto(UserStatus estado) {
        User usuario = crearUsuario("DNI", "12345678");
        usuario.setEstado(estado);
        repository.saveAndFlush(usuario);
        entityManager.clear();

        Object almacenado = entityManager.createNativeQuery("select estado from users where id = :id")
                .setParameter("id", usuario.getId())
                .getSingleResult();

        assertThat(almacenado).isEqualTo(estado.name());
        assertThat(repository.findById(usuario.getId()).orElseThrow().getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Actualiza la fecha técnica conservando la fecha de creación")
    void actualizaAuditoriaTecnica() {
        User usuario = repository.saveAndFlush(crearUsuario("DNI", "12345678"));
        var creacion = usuario.getCreatedAt();
        var actualizacion = usuario.getUpdatedAt();

        usuario.setNombreTratamiento("Anita");
        repository.flush();
        entityManager.clear();
        User actualizado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(actualizado.getCreatedAt()).isEqualTo(creacion);
        assertThat(actualizado.getUpdatedAt()).isAfterOrEqualTo(actualizacion);
        assertThat(actualizado.getNombreTratamiento()).isEqualTo("Anita");
    }

    @Test
    @DisplayName("Rechaza un documento con tipo o número vacío")
    void rechazaDocumentoVacio() {
        User usuario = crearUsuario(" ", "");

        assertThatThrownBy(() -> repository.saveAndFlush(usuario))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("La representación textual excluye los datos personales")
    void omiteDatosPersonalesEnToString() {
        User usuario = crearUsuario("DNI", "12345678");

        assertThat(usuario.toString()).contains("ACTIVE")
                .doesNotContain("12345678", "ana@example.test", "555123456", "Pérez");
    }

    @Test
    @DisplayName("La identidad persistida conserva igualdad y hash entre recargas")
    void mantieneIdentidadEntreRecargas() {
        User usuario = crearUsuario("DNI", "12345678");
        User distinto = crearUsuario("DNI", "87654321");
        int hashInicial = usuario.hashCode();
        assertThat(usuario).isEqualTo(usuario).isNotEqualTo(distinto).isNotEqualTo("otro");
        repository.saveAndFlush(usuario);
        entityManager.clear();

        User recargado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(usuario).isEqualTo(recargado);
        assertThat(recargado.hashCode()).isEqualTo(hashInicial);
    }

    private User crearUsuario(String tipo, String numero) {
        Documento documento = new Documento();
        documento.setTipo(tipo);
        documento.setNumero(numero);
        User usuario = new User();
        usuario.setDocumento(documento);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        usuario.setEmail("ana@example.test");
        usuario.setTelefono("555123456");
        return usuario;
    }
}
