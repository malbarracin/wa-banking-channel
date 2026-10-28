package com.bcra.orchestrator.dedup;

import com.bcra.orchestrator.messaging.consumer.InboundMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DedupeService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public DedupeService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public boolean process(InboundMessage message) {
        int inserted = jdbc.update("""
                INSERT INTO message_dedupe
                    (id, message_id, conversation_id, correlation_id, event_type, processed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), message.messageId(), message.conversationId(), message.correlationId(),
                "MessageReceived", Timestamp.from(Instant.now()));
        if (inserted == 0) {
            return false;
        }
        events.publishEvent(message);
        return true;
    }
}
