package com.bcra.credentials.ws.credencial;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse;
import com.bcra.credentials.ws.credencial.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.credentials.ws.credencial.client.ClienteNoEncontradoException;
import com.bcra.credentials.ws.credencial.client.UsersServiceClient;
import com.bcra.credentials.ws.credencial.client.UsersServiceUnavailableException;
import com.bcra.credentials.ws.credencial.dto.ValidarCredencialRequest;
import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.CredencialEstado;
import com.bcra.credentials.ws.credencial.entity.CredencialSesion;
import com.bcra.credentials.ws.credencial.repository.CredencialAuditLogRepository;
import com.bcra.credentials.ws.credencial.repository.CredencialRepository;
import com.bcra.credentials.ws.credencial.service.JwtService;
import com.bcra.credentials.ws.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "credencial.jwt.secret=clave-exclusiva-de-tests-256-bits-minimo")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidacionIntegrationTest {

    private static final String SECRET = "clave-exclusiva-de-tests-256-bits-minimo";
    private static final String NUMERO = "+5491112345678";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CredencialRepository repository;
    @Autowired
    private CredencialAuditLogRepository auditRepository;
    @MockitoBean
    private UsersServiceClient usersClient;

    private Instant expiracion;
    private String jti;

    @BeforeEach
    void preparar() {
        auditRepository.deleteAll();
        repository.deleteAll();
        expiracion = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jti = UUID.randomUUID().toString();
        when(usersClient.consultarEstado("DNI", "12345678"))
                .thenReturn(new ClienteEstadoResponse(EstadoCliente.ACTIVE));
    }

    @Test
    @DisplayName("Confirma la identidad persistida cuando JWT, base y H1 autorizan")
    void confirmaCredencialVigente() throws Exception {
        guardar(CredencialEstado.ACTIVA, expiracion);

        mockMvc.perform(post("/api/v1/credenciales/validar").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ValidarCredencialRequest(token("valido")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.numeroWhatsapp").value(NUMERO))
                .andExpect(jsonPath("$.documentoTipo").value("DNI"))
                .andExpect(jsonPath("$.documentoNumero").value("12345678"))
                .andExpect(jsonPath("$.fechaExpiracion").value(expiracion.toString()))
                .andExpect(jsonPath("$.credencial").doesNotExist());
        assertThat(auditRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"firma", "algoritmo", "malformado", "expirado", "sinExp", "sinJti",
        "sinSub", "documento", "numero", "vencimiento", "inexistente", "revocada", "expiradaBd",
        "vencidaBd", "suspendido", "borrado", "h1Ausente", "h1Caido", "vacio", "nulo", "sinIat", "iatFuturo"})
    @DisplayName("Rechaza y audita credenciales inválidas sin revelar el motivo al consumidor")
    void rechazaCredencial(String caso) throws Exception {
        CredencialEstado estado = switch (caso) {
            case "revocada" -> CredencialEstado.REVOCADA;
            case "expiradaBd" -> CredencialEstado.EXPIRADA;
            default -> CredencialEstado.ACTIVA;
        };
        guardar(estado, "vencidaBd".equals(caso) ? Instant.now().minusSeconds(1) : expiracion);
        switch (caso) {
            case "suspendido" -> when(usersClient.consultarEstado("DNI", "12345678"))
                    .thenReturn(new ClienteEstadoResponse(EstadoCliente.SUSPENDED));
            case "borrado" -> when(usersClient.consultarEstado("DNI", "12345678"))
                    .thenReturn(new ClienteEstadoResponse(EstadoCliente.LOGICAL_DELETE));
            case "h1Ausente" -> when(usersClient.consultarEstado("DNI", "12345678"))
                    .thenThrow(new ClienteNoEncontradoException());
            case "h1Caido" -> when(usersClient.consultarEstado("DNI", "12345678"))
                    .thenThrow(new UsersServiceUnavailableException());
            default -> { }
        }
        String jwt = token(caso);
        String body = "nulo".equals(caso) ? "{}"
                : objectMapper.writeValueAsString(new ValidarCredencialRequest(jwt));

        mockMvc.perform(post("/api/v1/credenciales/validar").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.credencial").doesNotExist());
        assertThat(auditRepository.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getAccion()).isEqualTo(AuditAccion.VALIDACION_FALLIDA);
            assertThat(evento.getMotivo()).isEqualTo("Credencial no válida");
        });
        if ("firma".equals(caso) || "malformado".equals(caso)) {
            assertThat(auditRepository.findAll().getFirst().getNumeroWhatsapp()).isNull();
            verifyNoInteractions(usersClient);
        }
    }

    @Test
    @DisplayName("Los DTOs ocultan token e identidad en toString")
    void protegeRepresentaciones() {
        assertThat(new ValidarCredencialRequest("secreto").toString()).doesNotContain("secreto");
        assertThat(new com.bcra.credentials.ws.credencial.dto.ValidarCredencialResponse(
                true, NUMERO, "DNI", "12345678", expiracion).toString()).doesNotContain(NUMERO, "12345678");
    }

    @Test
    @DisplayName("Una clave ausente o corta impide validar y los tokens excesivos se rechazan")
    void exigeConfiguracionSegura() {
        assertThatThrownBy(() -> new JwtService("").verificar("token"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(SECRET).verificar("x".repeat(8193)))
                .isInstanceOf(UnauthorizedException.class);
    }

    private void guardar(CredencialEstado estado, Instant fechaExpiracion) {
        repository.save(CredencialSesion.builder().jti(jti).numeroWhatsapp(NUMERO)
                .documentoTipo("DNI").documentoNumero("12345678").vinculoId(UUID.randomUUID())
                .estado(estado).fechaEmision(Instant.now().minusSeconds(60))
                .fechaExpiracion(fechaExpiracion).build());
    }

    private String token(String caso) throws Exception {
        if ("malformado".equals(caso)) {
            return "no-es-un-jwt";
        }
        if ("vacio".equals(caso)) {
            return "";
        }
        var claims = new JWTClaimsSet.Builder().issueTime(Date.from("iatFuturo".equals(caso)
                ? Instant.now().plusSeconds(60) : Instant.now().minusSeconds(60)))
                .subject("numero".equals(caso) ? "+5491199999999" : NUMERO)
                .jwtID("inexistente".equals(caso) ? "otro" : jti)
                .claim("doc", "documento".equals(caso) ? "DNI:otro" : "DNI:12345678")
                .expirationTime(Date.from(switch (caso) {
                    case "expirado" -> Instant.now().minusSeconds(120);
                    case "vencimiento" -> expiracion.plusSeconds(1);
                    default -> expiracion;
                }));
        Map<String, String> ausentes = Map.of("sinExp", "exp", "sinJti", "jti", "sinSub", "sub", "sinIat", "iat");
        if (ausentes.containsKey(caso)) {
            claims.claim(ausentes.get(caso), null);
        }
        var jwt = new SignedJWT(new JWSHeader("algoritmo".equals(caso) ? JWSAlgorithm.HS384 : JWSAlgorithm.HS256),
                claims.build());
        String clave = "firma".equals(caso) || "algoritmo".equals(caso) ? SECRET + "otra-clave-larga" : SECRET;
        jwt.sign(new MACSigner(clave.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
