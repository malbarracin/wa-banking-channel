package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcra.users.ws.config.BankUsersProperties;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaUsersServiceApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BankUsersProperties properties;
    @Autowired
    private Flyway flyway;

    @Test
    void loadsConfigurationAndMigratesSchema() {
        assertThat(properties.requiredFields()).containsExactly("uniquenessValue", "displayName");
        assertThat(properties.uniquenessField()).isEqualTo("uniquenessValue");
        assertThat(properties.updatableFields()).contains("contactEmail");
        assertThat(properties.publicFields()).contains("id");
        assertThat(properties.allowedTransitions().get("ACTIVE")).containsExactly("SUSPENDED", "INACTIVE");
        assertThat(properties.allowedTransitions().get("INACTIVE")).isEqualTo(List.of());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_entries", Integer.class)).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void mainStartsApplication() {
        WaUsersServiceApplication.main(new String[] {"--spring.main.web-application-type=none",
            "--spring.profiles.active=test"});
    }
}
