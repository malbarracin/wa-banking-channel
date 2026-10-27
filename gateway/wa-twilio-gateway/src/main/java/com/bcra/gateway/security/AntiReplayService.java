package com.bcra.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Bounded, single-process pilot store. Only confirmed publications become duplicates. */
@Service
public class AntiReplayService {
    public enum Claim { NEW, DUPLICATE, BUSY }

    private final Map<String, Instant> completed = new HashMap<>();
    private final Map<String, Boolean> inFlight = new HashMap<>();
    private final Clock clock;
    private final Duration window;
    private final int capacity;

    @Autowired
    public AntiReplayService(@Value("${WEBHOOK_ANTI_REPLAY_WINDOW_SECONDS:300}") long seconds,
            @Value("${WEBHOOK_ANTI_REPLAY_CAPACITY:10000}") int capacity) {
        this(Clock.systemUTC(), Duration.ofSeconds(seconds), capacity);
    }

    AntiReplayService(Clock clock, Duration window, int capacity) {
        if (window.isNegative() || window.isZero() || capacity < 1) {
            throw new IllegalArgumentException("Invalid replay store configuration");
        }
        this.clock = clock;
        this.window = window;
        this.capacity = capacity;
    }

    public synchronized Claim claim(String messageSid) {
        Instant now = clock.instant();
        completed.values().removeIf(expiry -> !expiry.isAfter(now));
        if (completed.containsKey(messageSid)) {
            return Claim.DUPLICATE;
        }
        if (inFlight.containsKey(messageSid) || completed.size() + inFlight.size() >= capacity) {
            return Claim.BUSY;
        }
        inFlight.put(messageSid, true);
        return Claim.NEW;
    }

    public synchronized void complete(String messageSid) {
        inFlight.remove(messageSid);
        completed.put(messageSid, clock.instant().plus(window));
    }

    public synchronized void release(String messageSid) {
        inFlight.remove(messageSid);
    }
}
