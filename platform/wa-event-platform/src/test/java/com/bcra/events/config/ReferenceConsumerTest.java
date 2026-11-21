package com.bcra.events.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

class ReferenceConsumerTest {
    @Test
    void subscribesToReceivedWithItsOwnGroupAndAcknowledges() throws Exception {
        KafkaListener listener = ReferenceConsumer.class.getMethod("receive", String.class, Acknowledgment.class)
            .getAnnotation(KafkaListener.class);
        assertThat(listener.topics()).containsExactly("${wa.topics.received}");
        assertThat(listener.groupId()).isEqualTo("${wa.topics.consumer-groups.reference}");
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        new ReferenceConsumer().receive("{}", acknowledgment);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void registersOnlyInLocalDevelopmentAndTestProfiles() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ReferenceConsumer.class);
        for (String profile : new String[]{"local", "dev", "test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasSingleBean(ReferenceConsumer.class));
        }
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(ReferenceConsumer.class));
    }
}
