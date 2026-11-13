package com.bcra.ws.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import com.bcra.ws.users.api.v1.controller.UserController;
import com.bcra.ws.users.api.v1.mapper.UserMapper;
import com.bcra.ws.users.config.NativeHints;
import com.bcra.ws.users.config.OpenApiConfig;
import com.bcra.ws.users.repository.UserAuditEventRepository;
import com.bcra.ws.users.repository.UserIndexInitializer;
import com.bcra.ws.users.repository.UserRepository;
import com.bcra.ws.users.service.UserAuditService;
import com.bcra.ws.users.service.UserService;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserAuditEventRepository auditEventRepository;

    @MockBean
    private UserIndexInitializer indexInitializer;

    @Test
    @DisplayName("Should discover API, services, generated mapper and configuration after package migration")
    void shouldDiscoverApplicationComponents() {
        assertThat(applicationContext.getBeansOfType(UserController.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(UserService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(UserAuditService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(UserMapper.class)).hasSize(1);
        assertThat(applicationContext.getBean(UserMapper.class).getClass().getPackageName())
                .isEqualTo("com.bcra.ws.users.api.v1.mapper");
        assertThat(applicationContext.getBeansOfType(OpenApiConfig.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(NativeHints.class)).hasSize(1);
    }
}
