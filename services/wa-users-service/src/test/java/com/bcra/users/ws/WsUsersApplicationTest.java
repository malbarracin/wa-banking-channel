package com.bcra.users.ws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WsUsersApplicationTest {
    @Test
    void contextLoads() {
    }

    @Test
    void mainStartsApplication() {
        WsUsersApplication.main(new String[]{"--spring.profiles.active=test", "--spring.main.web-application-type=none"});
    }
}
