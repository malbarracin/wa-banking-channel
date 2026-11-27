package com.bcra.users.ws.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"bank.users.required-fields=contactEmail", "bank.users.uniqueness-field=contactEmail",
        "bank.users.public-fields=status",
        "spring.datasource.url=jdbc:h2:mem:alternatepolicy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlternatePolicyTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void usesConfiguredRequiredAndUniqueField() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("contactEmail"));
        String body = "{\"contactEmail\":\"" + UUID.randomUUID() + "@example.com\"}";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists());
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details[0].field").value("contactEmail"));
    }

    @Test
    void rejectsExistingEmailCreatedUnderPreviousPolicy() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users (id, external_id, uniqueness_field, contact_email, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), email);
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"" + email + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details[0].field").value("contactEmail"));
    }

    @Test
    void concurrentCreationsAllowOnlyOneCustomer() throws Exception {
        String body = "{\"contactEmail\":\"" + UUID.randomUUID() + "@example.com\"}";
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }
}
