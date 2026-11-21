package com.bcra.events.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record MessageReadyToSendPayload(String to, MessageKind kind, JsonNode text, JsonNode template,
                                        String replyToMessageId) { }
