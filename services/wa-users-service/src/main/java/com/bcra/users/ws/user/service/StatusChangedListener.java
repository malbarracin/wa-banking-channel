package com.bcra.users.ws.user.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StatusChangedListener {
    private final StatusChangedPublisher publisher;

    public StatusChangedListener(StatusChangedPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(StatusChangedEvent event) {
        publisher.publish(event);
    }
}
