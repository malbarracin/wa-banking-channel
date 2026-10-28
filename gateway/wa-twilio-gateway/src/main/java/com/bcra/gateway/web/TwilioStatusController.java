package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.bcra.gateway.normalization.StatusNormalizer;
import com.bcra.gateway.security.AntiReplayService;
import com.twilio.security.RequestValidator;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TwilioStatusController {
    private final RequestValidator validator;
    private final String publicUrl;
    private final StatusNormalizer normalizer;
    private final StatusEventPublisher publisher;
    private final AntiReplayService replay;
    private long rateWindowStarted = System.nanoTime();
    private int requestsInWindow;

    private synchronized boolean allowRequest() {
        long now = System.nanoTime();
        if (now - rateWindowStarted >= TimeUnit.MINUTES.toNanos(1)) {
            rateWindowStarted = now;
            requestsInWindow = 0;
        }
        return ++requestsInWindow <= 120;
    }

    public TwilioStatusController(@Value("${TWILIO_AUTH_TOKEN}") String token,
            @Value("${TWILIO_STATUS_CALLBACK_PUBLIC_URL:}") String publicUrl,
            StatusNormalizer normalizer, StatusEventPublisher publisher, AntiReplayService replay) {
        this.validator = new RequestValidator(token);
        this.publicUrl = publicUrl;
        this.normalizer = normalizer;
        this.publisher = publisher;
        this.replay = replay;
    }

    @PostMapping(path = "/webhooks/twilio/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestBody byte[] body,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestHeader(value = "correlationId", required = false) String correlation) {
        if (!allowRequest()) {
            return ResponseEntity.status(429).build();
        }
        if (body.length > 16384) {
            return ResponseEntity.status(413).build();
        }
        Map<String, String> form = new HashMap<>();
        try {
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                if (form.putIfAbsent(key, value) != null) {
                    return ResponseEntity.badRequest().build();
                }
            }
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (publicUrl.isBlank() || signature == null || !validator.validate(publicUrl, form, signature)) {
            return ResponseEntity.status(403).build();
        }
        String correlationId;
        try {
            correlationId = correlation == null
                    ? (MDC.get("correlationId") == null ? UUID.randomUUID().toString() : MDC.get("correlationId"))
                    : UUID.fromString(correlation).toString();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        String previous = MDC.get("correlationId");
        String key = null;
        try {
            MDC.put("correlationId", correlationId);
            var event = normalizer.normalize(form, correlationId);
            key = "status:" + event.path("messageId").asText() + ":" + event.path("payload").path("status").asText();
            var claim = replay.claim(key);
            if (claim == AntiReplayService.Claim.DUPLICATE) {
                return ResponseEntity.ok().build();
            }
            if (claim == AntiReplayService.Claim.BUSY) {
                key = null;
                return ResponseEntity.status(503).build();
            }
            publisher.publish(event).toCompletableFuture().get(500, TimeUnit.MILLISECONDS);
            replay.complete(key);
            return ResponseEntity.ok().header("correlationId", correlationId).build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        } catch (Exception exception) {
            return ResponseEntity.status(503).build();
        } finally {
            if (key != null) {
                replay.release(key);
            }
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
