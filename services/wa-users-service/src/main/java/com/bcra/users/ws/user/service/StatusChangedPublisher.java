package com.bcra.users.ws.user.service;

public interface StatusChangedPublisher {
    void publish(StatusChangedEvent event);
}
