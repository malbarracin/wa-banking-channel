package com.bcra.ws.users.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies that entity identity does not depend on mutable personal or audit data. */
class UserEntityTest {

    @Test
    @DisplayName("Should identify users only by id despite personal data changes")
    void shouldIdentifyUsersOnlyById() {
        UserEntity original = UserEntity.builder().id("user-1").email("first@example.com").build();
        UserEntity changed = UserEntity.builder().id("user-1").email("changed@example.com").build();
        UserEntity other = UserEntity.builder().id("user-2").email("first@example.com").build();

        assertThat(original).isEqualTo(changed).hasSameHashCodeAs(changed).isNotEqualTo(other);
        assertThat(original).isNotEqualTo(null).isNotEqualTo("user-1");
        assertThat(original.toString()).doesNotContain("first@example.com");
    }

    @Test
    @DisplayName("Should identify audit events only by id despite summary changes")
    void shouldIdentifyAuditEventsOnlyById() {
        UserAuditEventEntity original = UserAuditEventEntity.builder()
                .id("audit-1").summary(Map.of("email", "first@example.com")).build();
        UserAuditEventEntity changed = UserAuditEventEntity.builder()
                .id("audit-1").summary(Map.of()).build();
        UserAuditEventEntity other = UserAuditEventEntity.builder().id("audit-2").build();

        assertThat(original).isEqualTo(changed).hasSameHashCodeAs(changed).isNotEqualTo(other);
        assertThat(original).isNotEqualTo(null).isNotEqualTo("audit-1");
        assertThat(original.toString()).doesNotContain("first@example.com");
    }
}
