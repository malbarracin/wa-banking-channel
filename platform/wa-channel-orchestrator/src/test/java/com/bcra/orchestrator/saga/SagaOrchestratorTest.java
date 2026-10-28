package com.bcra.orchestrator.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.orchestrator.conversation.service.ConversationService;
import com.bcra.orchestrator.saga.actions.SagaSignal;
import com.bcra.orchestrator.saga.compensation.SagaCompensation;
import com.bcra.orchestrator.saga.compensation.CompensationFailedException;
import com.bcra.orchestrator.saga.model.SagaStatus;
import com.bcra.orchestrator.saga.model.SagaStep;
import com.bcra.orchestrator.saga.model.SagaType;
import com.bcra.orchestrator.saga.orchestrator.SagaOrchestrator;
import com.bcra.orchestrator.saga.orchestrator.SagaTimeoutScheduler;
import com.bcra.orchestrator.saga.repository.SagaInstanceRepository;
import com.bcra.orchestrator.saga.repository.SagaStepExecutionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles("test")
public class SagaOrchestratorTest {
    @Autowired
    private SagaOrchestrator orchestrator;
    @Autowired
    private ConversationService conversations;
    @Autowired
    private SagaInstanceRepository sagas;
    @Autowired
    private SagaStepExecutionRepository executions;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void resumesOnboardingFromPersistedStateWithNewOrchestrator() {
        UUID id = start(SagaType.ONBOARDING);
        orchestrator.signal(id, SagaStep.LINK, SagaSignal.WAIT_FOR_INPUT);
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.WAITING);
        SagaOrchestrator restarted = engine(false);
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                restarted.signal(id, SagaStep.LINK, SagaSignal.SUCCEEDED));
        assertThat(sagas.findById(id).orElseThrow().getCurrentStep()).isEqualTo("ISSUE_CREDENTIAL");
        orchestrator.signal(id, SagaStep.ISSUE_CREDENTIAL, SagaSignal.SUCCEEDED);
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(executions.findBySagaIdOrderByStartedAtAsc(id)).hasSize(2)
                .allMatch(step -> step.getStatus() == SagaStatus.COMPLETED);
        assertThatThrownBy(() -> orchestrator.signal(id, SagaStep.ISSUE_CREDENTIAL, SagaSignal.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completesReverificationAndRejectsOutOfOrderSignals() {
        UUID id = start(SagaType.REVERIFICATION);
        assertThatThrownBy(() -> orchestrator.signal(id, SagaStep.LINK, SagaSignal.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class);
        orchestrator.signal(id, SagaStep.VERIFY_IDENTITY, SagaSignal.SUCCEEDED);
        orchestrator.signal(id, SagaStep.ISSUE_CREDENTIAL, SagaSignal.SUCCEEDED);
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void compensatesCompletedAndUncertainStepsInReverseOrder() {
        UUID id = start(SagaType.ONBOARDING);
        orchestrator.signal(id, SagaStep.LINK, SagaSignal.SUCCEEDED);
        List<SagaStep> compensatedSteps = new ArrayList<>();
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("compensation", (SagaCompensation) (sagaId, step) -> {
            compensatedSteps.add(step);
            return true;
        });
        SagaOrchestrator compensating = new SagaOrchestrator(sagas, executions,
                factory.getBeanProvider(SagaCompensation.class), Duration.ofHours(24));
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                compensating.signal(id, SagaStep.ISSUE_CREDENTIAL, SagaSignal.FAILED));
        assertThat(compensatedSteps).containsExactly(SagaStep.ISSUE_CREDENTIAL, SagaStep.LINK);
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(executions.findBySagaIdOrderByStartedAtAsc(id))
                .allMatch(step -> step.getStatus() == SagaStatus.COMPENSATED);
    }

    @Test
    void recordsSupportWhenCompensationThrows() {
        UUID id = start(SagaType.REVERIFICATION);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("compensation", (SagaCompensation) (sagaId, step) -> {
            throw new CompensationFailedException(new IllegalStateException("remote unavailable"));
        });
        SagaOrchestrator failing = new SagaOrchestrator(sagas, executions,
                factory.getBeanProvider(SagaCompensation.class), Duration.ofHours(24));
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                failing.signal(id, SagaStep.VERIFY_IDENTITY, SagaSignal.FAILED));
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    void expiresWaitingSagaAndPersistsSupportRequirementWithoutAdapter() {
        UUID id = start(SagaType.ONBOARDING);
        orchestrator.signal(id, SagaStep.LINK, SagaSignal.WAIT_FOR_INPUT);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var saga = sagas.findById(id).orElseThrow();
            saga.recordProgress(SagaStatus.WAITING, "LINK", "{}", Instant.EPOCH, Instant.now());
        });
        new SagaTimeoutScheduler(sagas, orchestrator).expireSagas();
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(executions.findBySagaIdOrderByStartedAtAsc(id)).singleElement()
                .satisfies(step -> assertThat(step.getResultCode()).isEqualTo("SUPPORT_REQUIRED"));
        orchestrator.expire(id, Instant.now());
        assertThat(executions.findBySagaIdOrderByStartedAtAsc(id)).hasSize(1);
    }

    @Test
    void startIsIdempotentAndDoesNotResetProgress() {
        UUID id = start(SagaType.ONBOARDING);
        var original = sagas.findById(id).orElseThrow();
        orchestrator.signal(id, SagaStep.LINK, SagaSignal.SUCCEEDED);
        assertThat(orchestrator.start(id, original.getConversationId(), SagaType.ONBOARDING).getCurrentStep())
                .isEqualTo("ISSUE_CREDENTIAL");
        assertThatThrownBy(() -> orchestrator.start(id, original.getConversationId(), SagaType.REVERIFICATION))
                .isInstanceOf(IllegalArgumentException.class);
        orchestrator.expire(id, Instant.EPOCH);
        assertThat(sagas.findById(id).orElseThrow().getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
    }

    private UUID start(SagaType type) {
        UUID id = UUID.randomUUID();
        conversations.loadOrCreate(id.toString(), UUID.randomUUID());
        orchestrator.start(id, id.toString(), type);
        return id;
    }

    private SagaOrchestrator engine(boolean compensate) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("compensation", (SagaCompensation) (id, step) -> compensate);
        return new SagaOrchestrator(sagas, executions, factory.getBeanProvider(SagaCompensation.class),
                Duration.ofHours(24));
    }
}
