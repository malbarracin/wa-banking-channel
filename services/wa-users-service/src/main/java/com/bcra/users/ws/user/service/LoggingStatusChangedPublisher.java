package com.bcra.users.ws.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingStatusChangedPublisher implements StatusChangedPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingStatusChangedPublisher.class);

    @Override
    public void publish(StatusChangedEvent event) {
        LOG.info("wa.users.status-changed userId={} estadoAnterior={} estadoNuevo={} correlationId={}",
                event.userId(), event.estadoAnterior(), event.estadoNuevo(), event.correlationId());
    }
}
