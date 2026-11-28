package com.bcra.users.ws;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("La base rechaza identificadores externos repetidos")
    void rejectsDuplicateExternalId() {
        insertUser("external-1", "unique-1");

        assertThatThrownBy(() -> insertUser("external-1", "unique-2"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private void insertUser(String externalId, String uniquenessValue) {
        jdbc.update("INSERT INTO users (id, external_id, uniqueness_field, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), externalId, uniquenessValue);
    }
}
