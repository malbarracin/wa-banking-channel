package com.bcra.gateway.outbound;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public class OutboundStore {
    private static final int MAX_ATTEMPTS = 4;
    private final JdbcTemplate jdbc;

    public OutboundStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS outbound_send (
                    message_id VARCHAR(256) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL,
                    conversation_id VARCHAR(256) NOT NULL, correlation_id VARCHAR(36) NOT NULL,
                    state VARCHAR(16) NOT NULL, attempts INTEGER NOT NULL,
                    sid VARCHAR(64), provider_status VARCHAR(16), error_code VARCHAR(64))
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS outbound_sid ON outbound_send(sid)");
    }

    public SendRecord claim(String messageId, String fingerprint, String conversationId, String correlationId) {
        try {
            jdbc.update("""
                    INSERT INTO outbound_send
                    (message_id, fingerprint, conversation_id, correlation_id, state, attempts)
                    VALUES (?, ?, ?, ?, 'SENDING', 1)
                    """, messageId, fingerprint, conversationId, correlationId);
            return find(messageId).orElseThrow();
        } catch (DuplicateKeyException exception) {
            SendRecord existing = find(messageId).orElseThrow();
            if (!existing.fingerprint().equals(fingerprint)
                    || !existing.conversationId().equals(conversationId)) {
                throw new OutboundException(OutboundException.Kind.PERMANENT, "MESSAGE_ID_CONFLICT");
            }
            if ("RETRYABLE".equals(existing.state()) && existing.attempts() < MAX_ATTEMPTS) {
                int claimed = jdbc.update("""
                        UPDATE outbound_send SET state='SENDING', attempts=attempts+1
                        WHERE message_id=? AND state='RETRYABLE' AND attempts=?
                        """, messageId, existing.attempts());
                if (claimed == 1) {
                    return find(messageId).orElseThrow();
                }
            }
            if ("SENT".equals(existing.state())) {
                return existing;
            }
            throw new OutboundException(OutboundException.Kind.AMBIGUOUS, "SEND_REQUIRES_RECONCILIATION");
        }
    }

    public void sent(String messageId, String sid, String status) {
        jdbc.update("UPDATE outbound_send SET state='SENT', sid=?, provider_status=? WHERE message_id=?",
                sid, status, messageId);
    }

    public void failed(String messageId, OutboundException failure) {
        jdbc.update("UPDATE outbound_send SET state=?, error_code=? WHERE message_id=?",
                failure.kind() == OutboundException.Kind.TRANSIENT ? "RETRYABLE" : "FAILED",
                failure.code(), messageId);
    }

    public Optional<SendRecord> find(String messageId) {
        return jdbc.query("SELECT * FROM outbound_send WHERE message_id=?", (row, index) -> new SendRecord(
                row.getString("message_id"), row.getString("fingerprint"), row.getString("conversation_id"),
                row.getString("correlation_id"), row.getString("state"), row.getInt("attempts"),
                row.getString("sid"), row.getString("provider_status")), messageId).stream().findFirst();
    }

    public Optional<SendRecord> findBySid(String sid) {
        return jdbc.query("SELECT message_id FROM outbound_send WHERE sid=?",
                (row, index) -> row.getString("message_id"), sid).stream().findFirst().flatMap(this::find);
    }

    public record SendRecord(String messageId, String fingerprint, String conversationId, String correlationId,
                             String state, int attempts, String sid, String status) { }
}
