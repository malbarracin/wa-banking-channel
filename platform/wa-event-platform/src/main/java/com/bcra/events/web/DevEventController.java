package com.bcra.events.web;

import com.bcra.events.config.KafkaHealthIndicator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
public class DevEventController {
    private final KafkaHealthIndicator kafkaHealth;

    public DevEventController(KafkaHealthIndicator kafkaHealth) {
        this.kafkaHealth = kafkaHealth;
    }

    @GetMapping("/dev/smoke")
    public ResponseEntity<Void> smoke(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (UnknownHostException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return Status.UP.equals(kafkaHealth.health().getStatus())
            ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
