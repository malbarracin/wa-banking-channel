package com.wa.banking.auth.integration.users;

import com.wa.banking.auth.exception.UserNotEligibleException;
import com.wa.banking.auth.integration.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Cliente HTTP opcional para validar usuarios bancarios en H1 antes de emitir credencial.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
public class UsersClient {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final IntegrationProperties integrationProperties;
    private final RestClient restClient;

    public UsersClient(IntegrationProperties integrationProperties) {
        this.integrationProperties = integrationProperties;
        this.restClient = RestClient.builder()
                .baseUrl(integrationProperties.getUsers().getBaseUrl())
                .build();
    }

    /**
     * Consulta un usuario por identificador interno (H1).
     *
     * @param userId identificador del usuario
     * @return usuario si existe
     */
    public Optional<UserResponseV1> findById(String userId) {
        if (!integrationProperties.getUsers().isEnabled()) {
            return Optional.empty();
        }
        return fetchById(userId);
    }

    /**
     * Valida elegibilidad del usuario en H1 cuando la integración está habilitada.
     *
     * @param bankUserId identificador del usuario bancario
     */
    public void assertEligibleForSession(String bankUserId) {
        if (!integrationProperties.getUsers().isEnabled()) {
            return;
        }
        UserResponseV1 user = fetchById(bankUserId)
                .orElseThrow(() -> new UserNotEligibleException(bankUserId));
        if (!ACTIVE_STATUS.equals(user.getStatus()) || !user.isCanLinkChannel()) {
            throw new UserNotEligibleException(bankUserId);
        }
    }

    private Optional<UserResponseV1> fetchById(String userId) {
        try {
            UserResponseV1 response = restClient.get()
                    .uri("/api/v1/users/{id}", userId)
                    .retrieve()
                    .body(UserResponseV1.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("Error calling H1 users service by id: {}", exception.getMessage());
            throw exception;
        }
    }
}
