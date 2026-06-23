package com.wa.banking.channel.integration.session;

import com.wa.banking.channel.config.IntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SessionClientTest {

    @Test
    @DisplayName("Should issue and revoke credential in stub mode")
    void shouldIssueAndRevokeCredential_whenStubEnabled() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.getSession().setStubEnabled(true);
        SessionClient client = new SessionClient(properties);

        SessionCredentialResponse credential = client.issueCredential("link-1", "user-1");

        assertThat(credential.getCredentialId()).startsWith("stub-cred-");
        assertThat(credential.getExpiresAt()).isNotNull();
        client.revokeCredential(credential.getCredentialId());
        client.revokeCredential(null);
        client.revokeCredential("   ");
    }

    @Test
    @DisplayName("Should call H2 REST API when stub is disabled")
    void shouldCallH2Api_whenStubDisabled() throws Exception {
        IntegrationProperties properties = new IntegrationProperties();
        properties.getSession().setStubEnabled(false);
        properties.getSession().setBaseUrl("http://localhost:8082");
        SessionClient client = new SessionClient(properties);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://localhost:8082");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        injectRestClient(client, restClientBuilder.build());

        server.expect(requestTo("http://localhost:8082/api/v1/sessions/credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "credentialId": "cred-remote-1",
                          "expiresAt": "2026-06-24T10:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessionCredentialResponse credential = client.issueCredential("link-1", "user-1");

        assertThat(credential.getCredentialId()).isEqualTo("cred-remote-1");
        server.verify();
    }

    private void injectRestClient(SessionClient client, RestClient restClient) throws Exception {
        Field field = SessionClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }
}
