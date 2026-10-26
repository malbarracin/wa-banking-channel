package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class VinculoAuditServiceIntegrationTest {

    private static final String NUMERO = "+5491112345678";

    private final VinculoAuditService service;
    private final VinculoAuditLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    VinculoAuditServiceIntegrationTest(VinculoAuditService service, VinculoAuditLogRepository repository,
            PlatformTransactionManager transactionManager) {
        this.service = service;
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void limpiarAuditoria() {
        repository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(AuditAccion.class)
    @DisplayName("Persiste cada acción con identidad, resultado y fecha")
    void registraCadaAccion(AuditAccion accion) {
        Instant inicio = Instant.now().minusSeconds(1);

        VinculoAuditLog registro = service.registrar(accion, AuditResultado.EXITOSO,
                NUMERO, "DNI", "12345678", null);
        VinculoAuditLog guardado = repository.findById(registro.getId()).orElseThrow();

        assertThat(guardado.getAccion()).isEqualTo(accion);
        assertThat(guardado.getResultado()).isEqualTo(AuditResultado.EXITOSO);
        assertThat(guardado.getNumeroWhatsapp()).isEqualTo(NUMERO);
        assertThat(guardado.getDocumentoTipo()).isEqualTo("DNI");
        assertThat(guardado.getDocumentoNumero()).isEqualTo("12345678");
        assertThat(guardado.getMotivo()).isNull();
        assertThat(guardado.getFecha()).isBetween(inicio, Instant.now().plusSeconds(1));
        assertThat(guardado.toString()).doesNotContain(NUMERO, "12345678");
    }

    @Test
    @DisplayName("Conserva el rechazo aunque la transacción llamadora revierta")
    void conservaEventoTrasRollback() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID id = transaccion.execute(estado -> {
            VinculoAuditLog registro = service.registrar(AuditAccion.VERIFICACION_FALLIDA,
                    AuditResultado.RECHAZADO, NUMERO, null, null, "Verificación rechazada");
            estado.setRollbackOnly();
            return registro.getId();
        });

        VinculoAuditLog guardado = repository.findById(id).orElseThrow();
        assertThat(guardado.getResultado()).isEqualTo(AuditResultado.RECHAZADO);
        assertThat(guardado.getDocumentoTipo()).isNull();
        assertThat(guardado.getDocumentoNumero()).isNull();
    }

    @ParameterizedTest
    @EnumSource(AuditAccion.class)
    @DisplayName("Nunca persiste secretos o respuestas de verificación recibidos en el motivo")
    void excluyeDatosSensiblesDelRegistro(AuditAccion accion) {
        String secretos = "otp=938471 token=eyJsecreto password=clave respuesta=mi primera mascota";

        service.registrar(accion, AuditResultado.RECHAZADO, NUMERO, "DNI", "12345678", secretos);

        VinculoAuditLog registro = repository.findAll().getFirst();
        assertThat(registro.getMotivo()).isEqualTo("Detalle omitido por seguridad");
        assertThat(List.of(registro.getNumeroWhatsapp(), registro.getDocumentoTipo(),
                registro.getDocumentoNumero(), registro.getMotivo(), registro.getAccion().name(),
                registro.getResultado().name(), registro.getId().toString(), registro.getFecha().toString()))
                .allSatisfy(valor -> assertThat(valor)
                        .doesNotContain("938471", "eyJsecreto", "clave", "mi primera mascota"));
    }

    @Test
    @DisplayName("Consulta solo los eventos del número en orden descendente e inmodificable")
    void consultaHistorialOrdenado() {
        VinculoAuditLog anterior = guardarEvento(Instant.parse("2026-01-01T00:00:00Z"));
        VinculoAuditLog reciente = guardarEvento(Instant.parse("2026-01-02T00:00:00Z"));
        service.registrar(AuditAccion.BLOQUEO, AuditResultado.EXITOSO, "+34123456789", null, null, "");

        List<VinculoAuditLog> historial = service.consultarHistorial(NUMERO);

        assertThat(historial).extracting(VinculoAuditLog::getId)
                .containsExactly(reciente.getId(), anterior.getId());
        assertThatThrownBy(historial::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.consultarHistorial("+34123456780")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12345", "+012345", "+1234567890123456", "otp=938471"})
    @DisplayName("Rechaza un número inválido sin persistirlo")
    void rechazaNumeroInvalido(String numero) {
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.VINCULACION, AuditResultado.EXITOSO, numero, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarHistorial(numero));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza acciones, resultados y referencias inválidas sin guardar eventos")
    void validaCamposDelEvento() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                null, AuditResultado.EXITOSO, NUMERO, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.BLOQUEO, null, NUMERO, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.BLOQUEO, AuditResultado.EXITOSO, NUMERO, " ", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.BLOQUEO, AuditResultado.EXITOSO, NUMERO, null, "a".repeat(256), null));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Revierte las escrituras externas y conserva solo la auditoría de la transacción nueva")
    void aislaAuditoriaDeEscriturasRevertidas() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID id = transaccion.execute(estado -> {
            guardarEvento(Instant.parse("2026-01-01T00:00:00Z"));
            repository.flush();
            VinculoAuditLog registro = service.registrar(AuditAccion.BLOQUEO,
                    AuditResultado.RECHAZADO, NUMERO, null, null, null);
            estado.setRollbackOnly();
            return registro.getId();
        });

        assertThat(repository.findAll()).extracting(VinculoAuditLog::getId).containsExactly(id);
        assertThat(service.consultarHistorial(NUMERO)).extracting(VinculoAuditLog::getId).containsExactly(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"938471", "mi primera mascota", "ZX9q7P2v", "\nBearer credencial-opaca\n"})
    @DisplayName("Omite secretos sin etiquetas reconocibles en el motivo")
    void excluyeSecretosSinPalabrasClave(String secreto) {
        service.registrar(AuditAccion.VERIFICACION_FALLIDA, AuditResultado.RECHAZADO,
                NUMERO, null, null, secreto);

        assertThat(repository.findAll()).singleElement().satisfies(registro ->
                assertThat(registro.getMotivo()).isEqualTo("Detalle omitido por seguridad").doesNotContain(secreto));
    }

    private VinculoAuditLog guardarEvento(Instant fecha) {
        return repository.save(VinculoAuditLog.builder()
                .accion(AuditAccion.ACEPTACION_TERMINOS)
                .resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp(NUMERO)
                .fecha(fecha)
                .build());
    }
}
