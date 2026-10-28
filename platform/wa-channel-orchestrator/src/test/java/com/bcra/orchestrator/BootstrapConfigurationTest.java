package com.bcra.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapConfigurationTest {

    @Test
    @DisplayName("Expone Actuator y permite configurar Kafka sin infraestructura externa")
    void exposesActuatorAndBindsExternalKafkaConfiguration() {
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "conversationStateRepository", org.mockito.Mockito.mock(
                        com.bcra.orchestrator.conversation.repository.ConversationStateRepository.class)));
        String[] arguments = {
            "--server.port=0",
            "--spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "--spring.kafka.bootstrap-servers=localhost:19092",
            "--spring.kafka.security.protocol=SSL"
        };

        try (var context = (ServletWebServerApplicationContext) application.run(arguments)) {
            KafkaProperties kafka = context.getBean(KafkaProperties.class);
            var listenerFactory = context.getBean("kafkaListenerContainerFactory",
                org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory.class);
            assertThat(listenerFactory.createContainer("wa.message.received.v1").getCommonErrorHandler())
                .isSameAs(context.getBean(org.springframework.kafka.listener.DefaultErrorHandler.class));
            RestClient client = RestClient.create("http://localhost:" + context.getWebServer().getPort());

            assertThat(kafka.getBootstrapServers()).containsExactly("localhost:19092");
            assertThat(kafka.getSecurity().getProtocol()).isEqualTo("SSL");
            assertThat(kafka.getProducer().getProperties()).containsEntry("enable.idempotence", "true");
            JsonNode health = client.get().uri("/actuator/health").retrieve().body(JsonNode.class);
            assertThat(health).isNotNull();
            assertThat(health.path("status").asText()).isEqualTo("UP");
            assertThat(health.has("components")).isFalse();
            assertThat(client.get().uri("/actuator/info").retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(200);
            assertThat(client.get().uri("/actuator/prometheus").retrieve().body(String.class))
                .contains("jvm_memory_used_bytes");
        }
    }
}
