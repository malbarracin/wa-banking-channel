package com.bcra.channel.ws.client;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP hacia wa-users-service (H1): {@code GET /api/v1/users/{id}}.
 * Solo consulta por id interno; H1 no expone búsqueda por {@code uniquenessValue}.
 */
@Slf4j
@Component
public class UsersServiceClient {

    private static final String USERS_BY_ID_PATH = "/api/v1/users/{id}";

    private final RestClient usersServiceRestClient;

    /**
     * @param usersServiceRestClient RestClient apuntando a la base URL de H1
     */
    public UsersServiceClient(RestClient usersServiceRestClient) {
        this.usersServiceRestClient = Objects.requireNonNull(usersServiceRestClient, "usersServiceRestClient");
    }

    /**
     * Consulta un cliente por su id interno en H1.
     *
     * @param userId id interno (UUID) del cliente en wa-users-service
     * @return resultado tipado según existencia y estado operativo
     * @throws UsersServiceUnavailableException si H1 responde 5xx, timeout o falla la red
     */
    public UserLookupResult findById(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        try {
            return usersServiceRestClient.get()
                    .uri(USERS_BY_ID_PATH, userId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                            return UserLookupResult.notFound();
                        }
                        if (response.getStatusCode().is5xxServerError()) {
                            log.warn("Users service returned {} for userId={}",
                                    response.getStatusCode().value(), userId);
                            throw new UsersServiceUnavailableException("Users service returned a server error");
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("Users service returned unexpected {} for userId={}",
                                    response.getStatusCode().value(), userId);
                            throw new UsersServiceUnavailableException("Users service returned an unexpected status");
                        }
                        UsersServiceUserResponse body = response.bodyTo(UsersServiceUserResponse.class);
                        if (body == null || body.status() == null) {
                            throw new UsersServiceUnavailableException("Users service returned an empty body");
                        }
                        return toLookupResult(body);
                    });
        } catch (UsersServiceUnavailableException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("Users service unreachable or timed out for userId={}", userId);
            throw new UsersServiceUnavailableException("Users service unreachable or timed out", exception);
        } catch (RestClientException exception) {
            log.warn("Users service call failed for userId={}", userId);
            throw new UsersServiceUnavailableException("Users service call failed", exception);
        }
    }

    private static UserLookupResult toLookupResult(UsersServiceUserResponse response) {
        UserLookupStatus lookupStatus = switch (response.status()) {
            case ACTIVE -> UserLookupStatus.FOUND_ACTIVE;
            case SUSPENDED -> UserLookupStatus.FOUND_SUSPENDED;
            case INACTIVE -> UserLookupStatus.FOUND_INACTIVE;
        };
        return new UserLookupResult(
                lookupStatus,
                response.id(),
                response.externalId(),
                response.displayName(),
                response.contactEmail(),
                response.contactPhone());
    }
}
