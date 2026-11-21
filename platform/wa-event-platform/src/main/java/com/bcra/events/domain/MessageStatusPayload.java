package com.bcra.events.domain;

public record MessageStatusPayload(String providerMessageId, MessageStatus status, String errorCode, String errorDetail) { }
