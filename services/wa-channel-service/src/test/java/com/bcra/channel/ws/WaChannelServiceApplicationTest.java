package com.bcra.channel.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.ChannelMessages;

@SpringBootTest
class WaChannelServiceApplicationTest {

    @Autowired
    private BankChannelProperties bankChannelProperties;

    @Autowired
    private ChannelMessages channelMessages;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("El contexto de Spring arranca con H2 y los beans base")
    void contextLoads() {
        assertThat(bankChannelProperties).isNotNull();
        assertThat(channelMessages).isNotNull();
    }

    @Test
    @DisplayName("Flyway aplica V3__create_channel_audit y crea channel_audit_entries")
    void flywayAppliesChannelAuditMigration() {
        assertThat(flyway.info().applied())
                .isNotEmpty()
                .anySatisfy(migration -> {
                    assertThat(migration.getVersion().getVersion()).isEqualTo("3");
                    assertThat(migration.getDescription()).containsIgnoringCase("channel");
                    assertThat(migration.getDescription()).containsIgnoringCase("audit");
                });

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE UPPER(table_name) = 'CHANNEL_AUDIT_ENTRIES'
                """,
                Integer.class);
        assertThat(tableCount).isEqualTo(1);
    }
}
