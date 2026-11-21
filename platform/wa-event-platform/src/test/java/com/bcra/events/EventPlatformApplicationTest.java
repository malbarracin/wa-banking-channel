package com.bcra.events;

import org.junit.jupiter.api.Test;

class EventPlatformApplicationTest {
    @Test
    void startsApplication() {
        EventPlatformApplication.main(new String[]{"--spring.main.web-application-type=none"});
    }
}
