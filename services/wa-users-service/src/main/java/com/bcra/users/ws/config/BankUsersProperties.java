package com.bcra.users.ws.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurable bank policies for users. */
@ConfigurationProperties(prefix = "bank.users")
public record BankUsersProperties(List<String> requiredFields, String uniquenessField, List<String> updatableFields,
                                  List<String> publicFields, Map<String, List<String>> allowedTransitions) {
}
