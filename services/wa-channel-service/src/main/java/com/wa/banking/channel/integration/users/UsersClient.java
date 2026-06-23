package com.wa.banking.channel.integration.users;

import com.wa.banking.channel.config.IntegrationProperties;
import com.wa.banking.channel.entity.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Cliente HTTP para consultar usuarios bancarios en H1 (servicio de usuarios).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
public class UsersClient {

    private final RestClient restClient;

    public UsersClient(IntegrationProperties integrationProperties) {
        this.restClient = RestClient.builder()
                .baseUrl(integrationProperties.getUsers().getBaseUrl())
                .build();
    }

    /**
     * Consulta un usuario por tipo y número de documento (H1 U2).
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return usuario si existe
     */
    public Optional<UserResponseV1> findByDocument(DocumentType documentType, String documentNumber) {
        try {
            UserResponseV1 response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/by-document")
                            .queryParam("documentType", documentType.name())
                            .queryParam("documentNumber", documentNumber)
                            .build())
                    .retrieve()
                    .body(UserResponseV1.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound notFound) {
            log.debug("User not found in H1 for document type {}", documentType);
            return Optional.empty();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("Error calling H1 users service: {}", exception.getMessage());
            throw exception;
        }
    }

    /**
     * Consulta un usuario por identificador interno (H1 U2).
     *
     * @param userId identificador del usuario
     * @return usuario si existe
     */
    public Optional<UserResponseV1> findById(String userId) {
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
