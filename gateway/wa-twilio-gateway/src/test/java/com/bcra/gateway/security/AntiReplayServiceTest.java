package com.bcra.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static com.bcra.gateway.security.AntiReplayService.Claim.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntiReplayServiceTest {
    @Test
    void boundsStoreAndExpiresCompletedMessages() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var store = new AntiReplayService(clock, Duration.ofSeconds(300), 1);
        assertThat(store.claim("one")).isEqualTo(NEW);
        assertThat(store.claim("one")).isEqualTo(BUSY);
        assertThat(store.claim("two")).isEqualTo(BUSY);
        store.complete("one");
        assertThat(store.claim("one")).isEqualTo(DUPLICATE);
        when(clock.instant()).thenReturn(start.plusSeconds(300));
        assertThat(store.claim("two")).isEqualTo(NEW);
        store.release("two");
        assertThat(store.claim("one")).isEqualTo(NEW);
    }

    @Test
    void rejectsInvalidConfiguration() {
        var clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        assertThatThrownBy(() -> new AntiReplayService(clock, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AntiReplayService(clock, Duration.ofSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
