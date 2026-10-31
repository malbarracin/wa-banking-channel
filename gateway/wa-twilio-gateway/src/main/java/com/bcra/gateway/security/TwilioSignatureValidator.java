package com.bcra.gateway.security;

import com.twilio.security.RequestValidator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwilioSignatureValidator {
    private final RequestValidator validator;
    private final String publicUrl;

    public TwilioSignatureValidator(@Value("${TWILIO_AUTH_TOKEN}") String token,
            @Value("${TWILIO_WEBHOOK_PUBLIC_URL}") String publicUrl) {
        if (token.isBlank() || publicUrl.isBlank()) {
            throw new IllegalArgumentException("Twilio configuration is required");
        }
        this.validator = new RequestValidator(token);
        this.publicUrl = publicUrl;
    }

    public boolean isValid(Map<String, String> parameters, String signature) {
        return signature != null && !signature.isBlank()
                && validator.validate(publicUrl, parameters, signature);
    }
}
