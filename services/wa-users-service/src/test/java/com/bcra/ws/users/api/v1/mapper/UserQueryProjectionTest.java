package com.bcra.ws.users.api.v1.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.bcra.ws.users.api.v1.dto.UserBasicResponse;
import com.bcra.ws.users.api.v1.dto.UserResponse;
import com.bcra.ws.users.domain.UserStatus;

class UserQueryProjectionTest {

    @Test
    @DisplayName("La política puede restringir soporte sin ampliar servicios u orígenes desconocidos")
    void shouldRestrictConfiguredActors() {
        var projection = new UserQueryProjection(Mappers.getMapper(UserMapper.class),
                Set.of("BUSINESS_OPERATIONS", "BANK_SERVICE", "unknown"));
        var user = UserResponse.builder().id("user-1").status(UserStatus.SUSPENDED).build();
        assertThat(projection.project(user, "BUSINESS_OPERATIONS")).isSameAs(user);
        for (String actor : new String[] {"SUPPORT", "BANK_SERVICE", "unknown", "", null}) {
            assertThat(projection.project(user, actor)).isInstanceOf(UserBasicResponse.class);
        }
    }

    @Test
    @DisplayName("Una política vacía limita todos los actores y recalcula elegibilidad desde el estado")
    void shouldRestrictAllActors() {
        var projection = new UserQueryProjection(Mappers.getMapper(UserMapper.class), Set.of());
        var user = UserResponse.builder().status(UserStatus.LOGICALLY_DELETED).channelLinkEligible(true).build();
        for (String actor : Set.of("BUSINESS_OPERATIONS", "SUPPORT", "BANK_SERVICE")) {
            assertThat(projection.project(user, actor)).isInstanceOfSatisfying(UserBasicResponse.class,
                    response -> assertThat(response.channelLinkEligible()).isFalse());
        }
    }
}
