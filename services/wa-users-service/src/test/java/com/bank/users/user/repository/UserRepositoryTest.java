package com.bank.users.user.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Persiste el usuario completo con preferencias JSON y timestamps")
    void persisteUsuarioCompleto() {
        User usuario = nuevoUsuario("DNI", "12345678");
        usuario.setEmail("cliente@example.test");
        usuario.setTelefonoContacto("+34910000000");
        Map<String, Object> preferencias = Map.of("idioma", "es", "avisos", true,
                "presentacion", Map.of("tema", "oscuro"));
        usuario.setPreferencias(preferencias);

        repository.saveAndFlush(usuario);
        entityManager.clear();
        User recuperado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(recuperado.getId()).isNotNull();
        assertThat(recuperado.getTipoDocumento()).isEqualTo("DNI");
        assertThat(recuperado.getNumeroDocumento()).isEqualTo("12345678");
        assertThat(recuperado.getNombreTratamiento()).isEqualTo("Cliente");
        assertThat(recuperado.getEmail()).isEqualTo("cliente@example.test");
        assertThat(recuperado.getTelefonoContacto()).isEqualTo("+34910000000");
        assertThat(recuperado.getPreferencias()).isEqualTo(preferencias);
        assertThat(recuperado.getStatus()).isEqualTo(UserStatus.ACTIVO);
        assertThat(recuperado.getCreatedAt()).isEqualTo(usuario.getCreatedAt());
        assertThat(recuperado.getUpdatedAt()).isEqualTo(usuario.getUpdatedAt());
        assertThat(recuperado.getCreatedAt()).isNotNull();
        assertThat(recuperado.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Admite contacto ausente y preferencias vacías")
    void persisteDatosMinimos() {
        User usuario = repository.saveAndFlush(nuevoUsuario("DNI", "12345678"));
        entityManager.clear();

        User recuperado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(recuperado.getEmail()).isNull();
        assertThat(recuperado.getTelefonoContacto()).isNull();
        assertThat(recuperado.getPreferencias()).isEmpty();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Rechaza el documento duplicado al forzar flush")
    void rechazaDocumentoDuplicado() {
        repository.saveAndFlush(nuevoUsuario("DNI", "12345678"));

        assertThatThrownBy(() -> repository.saveAndFlush(nuevoUsuario("DNI", "12345678")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Permite el mismo número con distinto tipo y comprueba el par completo")
    void distingueTiposDeDocumento() {
        repository.saveAndFlush(nuevoUsuario("DNI", "12345678"));
        repository.saveAndFlush(nuevoUsuario("PASAPORTE", "12345678"));
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.existsByTipoDocumentoAndNumeroDocumento("DNI", "12345678")).isTrue();
        assertThat(repository.existsByTipoDocumentoAndNumeroDocumento("PASAPORTE", "12345678")).isTrue();
        assertThat(repository.existsByTipoDocumentoAndNumeroDocumento("OTRO", "12345678")).isFalse();
        assertThat(repository.existsByTipoDocumentoAndNumeroDocumento("DNI", "87654321")).isFalse();
    }

    @Test
    @DisplayName("Actualiza timestamps y almacena los estados como texto")
    void actualizaEstadoYTimestamp() {
        User usuario = repository.saveAndFlush(nuevoUsuario("DNI", "12345678"));
        Instant creacion = usuario.getCreatedAt();
        Instant actualizacion = usuario.getUpdatedAt();
        usuario.setStatus(UserStatus.SUSPENDIDO);

        repository.flush();
        entityManager.clear();
        User recuperado = repository.findById(usuario.getId()).orElseThrow();

        assertThat(recuperado.getCreatedAt()).isEqualTo(creacion);
        assertThat(recuperado.getUpdatedAt()).isAfter(actualizacion);
        assertThat(entityManager.getEntityManager().createNativeQuery("select status from users").getSingleResult())
                .isEqualTo("SUSPENDIDO");
        recuperado.setStatus(UserStatus.BAJA_LOGICA);
        repository.flush();
        entityManager.clear();
        assertThat(repository.findById(usuario.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.BAJA_LOGICA);
    }

    @Test
    @DisplayName("Las preferencias no exponen el mapa mutable y toString omite PII")
    void protegePreferenciasYRepresentacion() {
        User usuario = nuevoUsuario("DNI", "12345678");
        usuario.setEmail("cliente@example.test");
        usuario.setTelefonoContacto("+34910000000");
        Map<String, Object> preferencias = new HashMap<>(Map.of("idioma", "es"));
        usuario.setPreferencias(preferencias);
        preferencias.put("idioma", "en");

        assertThat(usuario.getPreferencias()).containsEntry("idioma", "es");
        assertThatThrownBy(() -> usuario.getPreferencias().put("tema", "oscuro"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(usuario.toString()).doesNotContain("DNI", "12345678", "Cliente", "cliente@example.test",
                "+34910000000", "idioma");
    }

    private User nuevoUsuario(String tipoDocumento, String numeroDocumento) {
        User usuario = new User();
        usuario.setTipoDocumento(tipoDocumento);
        usuario.setNumeroDocumento(numeroDocumento);
        usuario.setNombreTratamiento("Cliente");
        return usuario;
    }
}
