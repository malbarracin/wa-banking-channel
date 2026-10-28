package com.bcra.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorApplicationTest {

    @Test
    void startsWithBaseConfigurationWithoutExternalInfrastructure() {
        String[] args = {
            "--spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "--spring.main.web-application-type=none"
        };
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "conversationStateRepository", org.mockito.Mockito.mock(
                        com.bcra.orchestrator.conversation.repository.ConversationStateRepository.class)));
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            assertThat(context.getBean(OrchestratorApplication.class)).isNotNull();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("server.port")).isEqualTo("8091");
            assertThat(environment.getProperty("spring.kafka.producer.acks")).isEqualTo("all");
            assertThat(environment.getProperty("spring.kafka.consumer.enable-auto-commit")).isEqualTo("false");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
        }
    }

    @Test
    void mainDelegatesArgumentsToSpringBoot() {
        try (var spring = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            String[] args = {"--server.port=8091"};
            OrchestratorApplication.main(args);
            spring.verify(() -> SpringApplication.run(OrchestratorApplication.class, args));
        }
    }
}
