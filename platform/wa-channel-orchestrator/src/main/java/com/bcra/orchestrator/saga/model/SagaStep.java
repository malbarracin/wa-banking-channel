package com.bcra.orchestrator.saga.model;

import java.util.List;

public enum SagaStep {
    LINK, VERIFY_IDENTITY, ISSUE_CREDENTIAL;

    public static List<SagaStep> flow(SagaType type) {
        return switch (type) {
            case ONBOARDING -> List.of(LINK, ISSUE_CREDENTIAL);
            case REVERIFICATION -> List.of(VERIFY_IDENTITY, ISSUE_CREDENTIAL);
        };
    }
}
