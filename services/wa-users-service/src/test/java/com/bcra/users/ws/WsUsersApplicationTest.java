package com.bcra.users.ws;

import com.bcra.users.ws.support.GatewayJwtTestConfig;
import com.bcra.users.ws.support.GatewayJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(GatewayJwtTestConfig.class)
class WsUsersApplicationTest {
    @Test
    void contextLoads() {
    }

    @Test
    void mainStartsApplication() {
        WsUsersApplication.main(new String[]{"--spring.profiles.active=test", "--spring.main.web-application-type=none"});
    }
}
