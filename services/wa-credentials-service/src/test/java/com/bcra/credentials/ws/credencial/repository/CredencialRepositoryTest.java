package com.bcra.credentials.ws.credencial.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.entity.MotivoRevocacion;
import com.bcra.credentials.ws.credencial.entity.RevocadaPor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
class CredencialRepositoryTest {

    @Autowired
    private CredencialRepository credenciales;

    @Autowired
    private CredencialAuditLogRepository auditoria;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Persiste y consulta la credencial por número, jti y documento con auditoría temporal")
    void persisteYConsultaCredencial() {
        var credencial = credenciales.saveAndFlush(crearCredencial("identificador-1"));
        var createdAt = credencial.getCreatedAt();
        assertThat(credencial.getId()).isNotNull();
        assertThat(createdAt).isNotNull();

        credencial.setEstado(CredencialEstado.REVOCADA);
        credencial.setMotivoRevocacion(MotivoRevocacion.BLOQUEO);
        credencial.setRevocadaPor(RevocadaPor.CANAL);
        credencial.setFechaRevocacion(Instant.now());
        credencial.setRiesgo(true);
        credenciales.flush();
        entityManager.clear();

        var recuperada = credenciales.findByJti("identificador-1").orElseThrow();
        assertThat(recuperada.getEstado()).isEqualTo(CredencialEstado.REVOCADA);
        assertThat(recuperada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.BLOQUEO);
        assertThat(recuperada.getRevocadaPor()).isEqualTo(RevocadaPor.CANAL);
        assertThat(recuperada.getFechaRevocacion()).isNotNull();
        assertThat(recuperada.isRiesgo()).isTrue();
        assertThat(recuperada.getCreatedAt()).isCloseTo(createdAt, within(1, ChronoUnit.MILLIS));
        assertThat(recuperada.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(credenciales.findByNumeroWhatsappAndEstado("+5491100000000", CredencialEstado.REVOCADA))
                .isPresent();
        assertThat(credenciales.findByDocumentoTipoAndDocumentoNumero("DNI", "12345678"))
                .extracting(CredencialSesion::getId).containsExactly(recuperada.getId());
        assertThat(credenciales.findByJti("inexistente")).isEmpty();
    }

    @Test
    @DisplayName("La base rechaza un jti duplicado")
    void rechazaJtiDuplicado() {
        credenciales.saveAndFlush(crearCredencial("duplicado"));

        assertThatThrownBy(() -> credenciales.saveAndFlush(crearCredencial("duplicado")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La auditoría conserva intentos rechazados sin credencial y ordena por fecha descendente")
    void consultaAuditoriaSinCredencialAsociada() {
        Instant fecha = Instant.parse("2026-10-26T10:00:00Z");
        var anterior = auditoria.saveAndFlush(CredencialAuditLog.builder()
                .accion(AuditAccion.EMISION).resultado(AuditResultado.RECHAZADO)
                .numeroWhatsapp("+5491100000000").motivo("Verificación pendiente").fecha(fecha).build());
        var posterior = auditoria.saveAndFlush(CredencialAuditLog.builder()
                .accion(AuditAccion.REVOCACION).resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp("+5491100000000").documentoTipo("DNI").documentoNumero("12345678")
                .jti("identificador-2").motivo("BLOQUEO").fecha(fecha.plusSeconds(60)).build());
        entityManager.clear();

        var historial = auditoria.findByNumeroWhatsappOrderByFechaDesc("+5491100000000");
        assertThat(historial).extracting(CredencialAuditLog::getId)
                .containsExactly(posterior.getId(), anterior.getId());
        assertThat(historial.getFirst().getAccion()).isEqualTo(AuditAccion.REVOCACION);
        assertThat(historial.getFirst().getResultado()).isEqualTo(AuditResultado.EXITOSO);
        assertThat(historial.getFirst().getJti()).isEqualTo("identificador-2");
        assertThat(historial.getFirst().getDocumentoTipo()).isEqualTo("DNI");
        assertThat(historial.getFirst().getDocumentoNumero()).isEqualTo("12345678");
        assertThat(historial.getFirst().getMotivo()).isEqualTo("BLOQUEO");
        assertThat(auditoria.findByNumeroWhatsappOrderByFechaDesc("+5491199999999")).isEmpty();
    }

    @Test
    @DisplayName("Las consultas discriminan número, estado y ambos componentes del documento")
    void filtraCredencialesPorIdentidadYEstado() {
        var activa = credenciales.saveAndFlush(crearCredencial("activa"));
        var expirada = crearCredencial("expirada");
        expirada.setEstado(CredencialEstado.EXPIRADA);
        credenciales.saveAndFlush(expirada);
        entityManager.clear();

        assertThat(credenciales.findByNumeroWhatsappAndEstado("+5491100000000", CredencialEstado.ACTIVA))
                .hasValueSatisfying(encontrada -> assertThat(encontrada.getId()).isEqualTo(activa.getId()));
        assertThat(credenciales.findByNumeroWhatsappAndEstado("+5491100000000", CredencialEstado.EXPIRADA))
                .hasValueSatisfying(encontrada -> assertThat(encontrada.getId()).isEqualTo(expirada.getId()));
        assertThat(credenciales.findByNumeroWhatsappAndEstado("+5491199999999", CredencialEstado.ACTIVA))
                .isEmpty();
        assertThat(credenciales.findByNumeroWhatsappAndEstado("+5491100000000", CredencialEstado.REVOCADA))
                .isEmpty();
        assertThat(credenciales.findByDocumentoTipoAndDocumentoNumero("DNI", "12345678"))
                .extracting(CredencialSesion::getId).containsExactlyInAnyOrder(activa.getId(), expirada.getId());
        assertThat(credenciales.findByDocumentoTipoAndDocumentoNumero("PASAPORTE", "12345678")).isEmpty();
        assertThat(credenciales.findByDocumentoTipoAndDocumentoNumero("DNI", "87654321")).isEmpty();
    }

    @Test
    @DisplayName("La base rechaza una credencial sin estado obligatorio")
    void rechazaCredencialSinEstado() {
        var credencial = crearCredencial("sin-estado");
        credencial.setEstado(null);

        assertThatThrownBy(() -> credenciales.saveAndFlush(credencial))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El historial excluye eventos de otros números aunque sean más recientes")
    void aislaHistorialPorNumero() {
        Instant fecha = Instant.parse("2026-10-26T10:00:00Z");
        var propio = auditoria.saveAndFlush(CredencialAuditLog.builder()
                .accion(AuditAccion.EMISION).resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp("+5491100000000").fecha(fecha).build());
        auditoria.saveAndFlush(CredencialAuditLog.builder()
                .accion(AuditAccion.RENOVACION).resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp("+5491199999999").fecha(fecha.plusSeconds(60)).build());
        entityManager.clear();

        assertThat(auditoria.findByNumeroWhatsappOrderByFechaDesc("+5491100000000"))
                .extracting(CredencialAuditLog::getId).containsExactly(propio.getId());
    }

    private CredencialSesion crearCredencial(String jti) {
        return CredencialSesion.builder().jti(jti).numeroWhatsapp("+5491100000000")
                .documentoTipo("DNI").documentoNumero("12345678").vinculoId(UUID.randomUUID())
                .estado(CredencialEstado.ACTIVA).fechaEmision(Instant.now())
                .fechaExpiracion(Instant.now().plusSeconds(3600)).build();
    }
}
