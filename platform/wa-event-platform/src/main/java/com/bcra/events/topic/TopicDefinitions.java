package com.bcra.events.topic;

public enum TopicDefinitions {
    RECEIVED("wa.message.received.v1"),
    READY_TO_SEND("wa.message.ready-to-send.v1"),
    STATUS("wa.message.status.v1"),
    DLQ("wa.message.dlq.v1");

    private final String topicName;

    TopicDefinitions(String topicName) {
        this.topicName = topicName;
    }

    public String topicName() {
        return topicName;
    }
}
