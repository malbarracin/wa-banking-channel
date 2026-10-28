package com.bcra.orchestrator.conversation;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.bcra.orchestrator.conversation.repository.ConversationStateRepository;
import com.bcra.orchestrator.conversation.service.ConversationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceMdcTest {

    @Test
    @DisplayName("Propaga la correlación al repositorio y limpia el hilo tras un fallo")
    void scopesCorrelationOnFailure() {
        ConversationStateRepository repository = mock(ConversationStateRepository.class);
        ConversationService service = new ConversationService(repository);
        UUID correlationId = UUID.randomUUID();
        when(repository.findById("conversation")).thenAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId.toString());
            throw new IllegalStateException("Persistence unavailable");
        });

        assertThatThrownBy(() -> service.loadOrCreate("conversation", correlationId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Restaura la correlación del llamador al terminar")
    void restoresCallerCorrelation() {
        ConversationStateRepository repository = mock(ConversationStateRepository.class);
        UUID correlationId = UUID.randomUUID();
        when(repository.findById("conversation")).thenAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId.toString());
            return Optional.empty();
        });
        MDC.put("correlationId", "outer");
        try {
            new ConversationService(repository).loadOrCreate("conversation", correlationId);
            assertThat(MDC.get("correlationId")).isEqualTo("outer");
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    @DisplayName("Rechaza contexto vacío o fuera de los límites de persistencia")
    void rejectsInvalidContext() {
        ConversationService service = new ConversationService(mock(ConversationStateRepository.class));
        UUID correlationId = UUID.randomUUID();
        assertThatThrownBy(() -> service.loadOrCreate(" ", correlationId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.loadOrCreate("x".repeat(129), correlationId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateState("conversation", correlationId, "", "message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateState("conversation", correlationId, "WAITING", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.loadOrCreate("conversation", null))
                .isInstanceOf(NullPointerException.class);
    }
}
