package com.bcra.credentials.ws.credencial.service;

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

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;
import com.bcra.credentials.ws.credencial.entity.CredencialAuditLog;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CredencialAuditServiceIntegrationTest {

    private static final String NUMERO = "+5491112345678";

    private final CredencialAuditService service;
    private final CredencialAuditLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    CredencialAuditServiceIntegrationTest(CredencialAuditService service, CredencialAuditLogRepository repository,
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

        CredencialAuditLog registro = service.registrar(accion, AuditResultado.EXITOSO,
                NUMERO, "DNI", "12345678", null);
        CredencialAuditLog guardado = repository.findById(registro.getId()).orElseThrow();

        assertThat(guardado.getAccion()).isEqualTo(accion);
        assertThat(guardado.getResultado()).isEqualTo(AuditResultado.EXITOSO);
        assertThat(guardado.getNumeroWhatsapp()).isEqualTo(NUMERO);
        assertThat(guardado.getDocumentoTipo()).isEqualTo("DNI");
        assertThat(guardado.getDocumentoNumero()).isEqualTo("12345678");
        assertThat(guardado.getMotivo()).isNull();
        assertThat(guardado.getJti()).isNull();
        assertThat(guardado.getFecha()).isBetween(inicio, Instant.now().plusSeconds(1));
        assertThat(guardado.toString()).doesNotContain(NUMERO, "12345678");
    }

    @Test
    @DisplayName("Conserva el rechazo aunque la transacción llamadora revierta")
    void conservaEventoTrasRollback() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID id = transaccion.execute(estado -> {
            CredencialAuditLog registro = service.registrar(AuditAccion.VALIDACION_FALLIDA,
                    AuditResultado.RECHAZADO, NUMERO, null, null, "Verificación rechazada");
            estado.setRollbackOnly();
            return registro.getId();
        });

        CredencialAuditLog guardado = repository.findById(id).orElseThrow();
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

        CredencialAuditLog registro = repository.findAll().getFirst();
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
        CredencialAuditLog anterior = guardarEvento(Instant.parse("2026-01-01T00:00:00Z"));
        CredencialAuditLog reciente = guardarEvento(Instant.parse("2026-01-02T00:00:00Z"));
        service.registrar(AuditAccion.REVOCACION, AuditResultado.EXITOSO, "+34123456789", null, null, "");

        List<CredencialAuditLog> historial = service.consultarHistorial(NUMERO);

        assertThat(historial).extracting(CredencialAuditLog::getId)
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
                AuditAccion.EMISION, AuditResultado.EXITOSO, numero, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarHistorial(numero));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Rechaza acciones, resultados y referencias inválidas sin guardar eventos")
    void validaCamposDelEvento() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                null, AuditResultado.EXITOSO, NUMERO, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.REVOCACION, null, NUMERO, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.REVOCACION, AuditResultado.EXITOSO, NUMERO, " ", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.EMISION, AuditResultado.EXITOSO, NUMERO, "a".repeat(31), null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                AuditAccion.REVOCACION, AuditResultado.EXITOSO, NUMERO, null, "a".repeat(51), null));
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Revierte las escrituras externas y conserva solo la auditoría de la transacción nueva")
    void aislaAuditoriaDeEscriturasRevertidas() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID id = transaccion.execute(estado -> {
            guardarEvento(Instant.parse("2026-01-01T00:00:00Z"));
            repository.flush();
            CredencialAuditLog registro = service.registrar(AuditAccion.REVOCACION,
                    AuditResultado.RECHAZADO, NUMERO, null, null, null);
            estado.setRollbackOnly();
            return registro.getId();
        });

        assertThat(repository.findAll()).extracting(CredencialAuditLog::getId).containsExactly(id);
        assertThat(service.consultarHistorial(NUMERO)).extracting(CredencialAuditLog::getId).containsExactly(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"938471", "mi primera mascota", "ZX9q7P2v", "\nBearer credencial-opaca\n"})
    @DisplayName("Omite secretos sin etiquetas reconocibles en el motivo")
    void excluyeSecretosSinPalabrasClave(String secreto) {
        service.registrar(AuditAccion.VALIDACION_FALLIDA, AuditResultado.RECHAZADO,
                NUMERO, null, null, secreto);

        assertThat(repository.findAll()).singleElement().satisfies(registro ->
                assertThat(registro.getMotivo()).isEqualTo("Detalle omitido por seguridad").doesNotContain(secreto));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("Permite omitir el motivo sin inventar un detalle de auditoría")
    void registraEventoSinMotivo(String motivo) {
        CredencialAuditLog registro = service.registrar(AuditAccion.REVOCACION, AuditResultado.EXITOSO,
                NUMERO, null, null, motivo);

        assertThat(repository.findById(registro.getId())).hasValueSatisfying(guardado -> {
            assertThat(guardado.getMotivo()).isNull();
            assertThat(guardado.getDocumentoTipo()).isNull();
            assertThat(guardado.getDocumentoNumero()).isNull();
        });
    }

    @Test
    @DisplayName("Acepta las referencias documentales en su longitud máxima sin truncarlas")
    void conservaReferenciasEnLongitudMaxima() {
        String documentoTipo = "T".repeat(30);
        String documentoNumero = "1".repeat(50);

        CredencialAuditLog registro = service.registrar(AuditAccion.EMISION, AuditResultado.EXITOSO,
                NUMERO, documentoTipo, documentoNumero, null);

        assertThat(repository.findById(registro.getId())).hasValueSatisfying(guardado -> {
            assertThat(guardado.getDocumentoTipo()).isEqualTo(documentoTipo);
            assertThat(guardado.getDocumentoNumero()).isEqualTo(documentoNumero);
        });
    }

    @Test
    @DisplayName("Un rechazo de auditoría no revierte las escrituras de la transacción llamadora")
    void permiteCommitExternoTrasRechazoDeAuditoria() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID id = transaccion.execute(estado -> {
            CredencialAuditLog externo = guardarEvento(Instant.parse("2026-01-01T00:00:00Z"));
            repository.flush();

            assertThatIllegalArgumentException().isThrownBy(() -> service.registrar(
                    AuditAccion.REVOCACION, null, NUMERO, null, null, null));
            return externo.getId();
        });

        assertThat(repository.findAll()).extracting(CredencialAuditLog::getId).containsExactly(id);
    }

    private CredencialAuditLog guardarEvento(Instant fecha) {
        return repository.save(CredencialAuditLog.builder()
                .accion(AuditAccion.EMISION)
                .resultado(AuditResultado.EXITOSO)
                .numeroWhatsapp(NUMERO)
                .fecha(fecha)
                .build());
    }
}
