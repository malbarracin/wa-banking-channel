package com.bcra.channel.ws.vinculo.client;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.VinculoAuditLog;
import com.bcra.channel.ws.vinculo.repository.VinculoAuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Continuidad transaccional ante fallos de revocación H2")
class RevocacionBestEffortIntegrationTest {

    private static final String NUMERO = "+5491112345678";
    private static final String BASE_URL = "http://credenciales.test";

    private final VinculoAuditLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    RevocacionBestEffortIntegrationTest(VinculoAuditLogRepository repository,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallosDeCredenciales")
    @DisplayName("Confirma la escritura local aun cuando falla la solicitud a H2")
    void confirmaEscrituraLocalTrasFalloDeH2(String escenario, ResponseCreator respuesta) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CredencialesServiceClient credenciales = new RestCredencialesServiceClient(builder.build());
        servidor.expect(requestTo(BASE_URL + "/api/v1/credenciales/%2B5491112345678/revocacion"))
                .andRespond(respuesta);
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        UUID identificador = transaccion.execute(estado -> {
            VinculoAuditLog registro = repository.saveAndFlush(VinculoAuditLog.builder()
                    .accion(AuditAccion.BLOQUEO)
                    .resultado(AuditResultado.EXITOSO)
                    .numeroWhatsapp(NUMERO)
                    .fecha(Instant.now())
                    .build());
            credenciales.solicitarRevocacion(NUMERO, "SOLICITUD_CLIENTE");
            assertThat(estado.isRollbackOnly()).isFalse();
            return registro.getId();
        });

        try {
            assertThat(repository.findById(identificador)).hasValueSatisfying(registro -> {
                assertThat(registro.getAccion()).isEqualTo(AuditAccion.BLOQUEO);
                assertThat(registro.getResultado()).isEqualTo(AuditResultado.EXITOSO);
            });
            servidor.verify();
        } finally {
            repository.deleteById(identificador);
        }
    }

    private static Stream<Arguments> fallosDeCredenciales() {
        return Stream.of(
                Arguments.of("Error HTTP 400", withStatus(HttpStatus.BAD_REQUEST)),
                Arguments.of("Error HTTP 503", withStatus(HttpStatus.SERVICE_UNAVAILABLE)),
                Arguments.of("Timeout", withException(new SocketTimeoutException())));
    }
}
