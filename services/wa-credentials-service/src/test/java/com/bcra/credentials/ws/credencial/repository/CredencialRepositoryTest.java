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

    private CredencialSesion crearCredencial(String jti) {
        return CredencialSesion.builder().jti(jti).numeroWhatsapp("+5491100000000")
                .documentoTipo("DNI").documentoNumero("12345678").vinculoId(UUID.randomUUID())
                .estado(CredencialEstado.ACTIVA).fechaEmision(Instant.now())
                .fechaExpiracion(Instant.now().plusSeconds(3600)).build();
    }
}
