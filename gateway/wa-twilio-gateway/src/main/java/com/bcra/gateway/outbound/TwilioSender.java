package com.bcra.gateway.outbound;

import java.net.URI;

import com.twilio.exception.ApiException;
import com.twilio.exception.ApiConnectionException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

public class TwilioSender {
    private final TwilioRestClient client;
    private final String from;
    private final URI callback;

    public TwilioSender(TwilioRestClient client, String from, URI callback) {
        if (!from.matches("whatsapp:\\+[1-9][0-9]{1,14}") || !"https".equals(callback.getScheme())
                || callback.getHost() == null || callback.getQuery() != null || callback.getFragment() != null) {
            throw new IllegalArgumentException("Invalid outbound sender or callback URL");
        }
        this.client = client;
        this.from = from;
        this.callback = callback;
    }

    public Receipt send(String destination, String body) {
        if (!destination.matches("whatsapp:\\+[1-9][0-9]{1,14}")) {
            throw new OutboundException(OutboundException.Kind.PERMANENT, "INVALID_DESTINATION");
        }
        try {
            Message message = Message.creator(new PhoneNumber(destination), new PhoneNumber(from), body)
                    .setStatusCallback(callback).create(client);
            return new Receipt(message.getSid(), message.getStatus().toString());
        } catch (ApiException exception) {
            int status = exception.getStatusCode() == null ? 0 : exception.getStatusCode();
            var kind = status == 0 ? OutboundException.Kind.AMBIGUOUS
                    : status == 429 || status >= 500 ? OutboundException.Kind.TRANSIENT
                    : OutboundException.Kind.PERMANENT;
            String code = status == 0 ? "TWILIO_CONNECTION_AMBIGUOUS"
                    : exception.getCode() == null ? "TWILIO_API_ERROR" : "TWILIO_" + exception.getCode();
            throw new OutboundException(kind, code);
        } catch (ApiConnectionException exception) {
            // A timed out POST may already have created the message; never send it blindly again.
            throw new OutboundException(OutboundException.Kind.AMBIGUOUS, "TWILIO_CONNECTION_AMBIGUOUS");
        }
    }

    public record Receipt(String sid, String status) { }
}
