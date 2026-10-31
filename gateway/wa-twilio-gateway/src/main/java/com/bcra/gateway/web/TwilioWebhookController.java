package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.InboundWebhookPublisher;
import com.bcra.gateway.security.AntiReplayService;
import com.bcra.gateway.security.TwilioSignatureValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TwilioWebhookController {
    private static final long PUBLICATION_TIMEOUT_MILLIS = 500;
    private static final int MAX_BODY_BYTES = 16384;
    private static final int REQUESTS_PER_MINUTE = 120;
    private static final int MAX_TRACKED_ADDRESSES = 10000;
    private final Map<String, Integer> requestsByAddress = new HashMap<>();
    private long rateWindowStarted = System.nanoTime();
    private final TwilioSignatureValidator signatureValidator;
    private final AntiReplayService antiReplay;
    private final ObjectProvider<InboundWebhookPublisher> publishers;

    public TwilioWebhookController(TwilioSignatureValidator signatureValidator, AntiReplayService antiReplay,
            ObjectProvider<InboundWebhookPublisher> publishers) {
        this.signatureValidator = signatureValidator;
        this.antiReplay = antiReplay;
        this.publishers = publishers;
    }

    @PostMapping(path = "/webhook/twilio", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> inbound(@RequestBody byte[] body,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            HttpServletRequest request) {
        if (!allowRequest(request.getRemoteAddr())) {
            return ResponseEntity.status(429).build();
        }
        if (body.length > MAX_BODY_BYTES) {
            return ResponseEntity.status(413).build();
        }
        Map<String, String> parameters = new HashMap<>();
        try {
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                if (parameters.putIfAbsent(key, value) != null) {
                    return ResponseEntity.badRequest().build();
                }
            }
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (!signatureValidator.isValid(parameters, signature)) {
            return ResponseEntity.status(403).build();
        }
        String messageSid = parameters.get("MessageSid");
        if (messageSid == null || !messageSid.matches("SM[0-9a-fA-F]{32}")) {
            return ResponseEntity.badRequest().build();
        }
        var claim = antiReplay.claim(messageSid);
        if (claim == AntiReplayService.Claim.DUPLICATE) {
            return ResponseEntity.ok().build();
        }
        if (claim == AntiReplayService.Claim.BUSY) {
            return ResponseEntity.status(503).build();
        }
        try {
            InboundWebhookPublisher publisher = publishers.getIfAvailable();
            if (publisher == null) {
                return ResponseEntity.status(503).build();
            }
            publisher.publish(Map.copyOf(parameters)).toCompletableFuture()
                    .get(PUBLICATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            antiReplay.complete(messageSid);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        } catch (Exception exception) {
            return ResponseEntity.status(503).build();
        } finally {
            antiReplay.release(messageSid);
        }
    }

    private synchronized boolean allowRequest(String address) {
        long now = System.nanoTime();
        if (now - rateWindowStarted >= TimeUnit.MINUTES.toNanos(1)) {
            rateWindowStarted = now;
            requestsByAddress.clear();
        }
        int count = requestsByAddress.getOrDefault(address, 0);
        if (count >= REQUESTS_PER_MINUTE
                || (count == 0 && requestsByAddress.size() >= MAX_TRACKED_ADDRESSES)) {
            return false;
        }
        requestsByAddress.put(address, count + 1);
        return true;
    }
}
