package com.bcra.events.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record MessageReceivedPayload(String channel, String from, String to, MessageKind kind,
                                     JsonNode text, JsonNode media, JsonNode location) { }
