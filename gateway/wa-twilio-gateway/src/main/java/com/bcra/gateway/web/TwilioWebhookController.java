package com.bcra.gateway.web;

import com.bcra.gateway.messaging.producer.InboundWebhookPublisher;
import com.bcra.gateway.security.AntiReplayService;
import com.bcra.gateway.security.TwilioSignatureValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TwilioWebhookController {
    private static final long PUBLICATION_TIMEOUT_MILLIS = 500;
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
    public ResponseEntity<Void> inbound(@RequestBody MultiValueMap<String, String> form,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        Map<String, String> parameters = new HashMap<>();
        for (var entry : form.entrySet()) {
            if (entry.getValue().size() != 1) {
                return ResponseEntity.badRequest().build();
            }
            parameters.put(entry.getKey(), entry.getValue().getFirst());
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        } catch (Exception exception) {
            return ResponseEntity.status(503).build();
        } finally {
            antiReplay.release(messageSid);
        }
    }
}
