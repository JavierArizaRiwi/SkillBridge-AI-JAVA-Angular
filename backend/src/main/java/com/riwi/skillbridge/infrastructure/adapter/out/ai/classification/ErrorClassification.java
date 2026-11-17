package com.riwi.skillbridge.infrastructure.adapter.out.ai.classification;

/**
 * Classification of an error encountered during AI candidate execution.
 * Distinguishes whether the failure is transient/recoverable (fallback can be attempted)
 * or non-recoverable (client error or invalid input where fallback must NOT be attempted).
 */
public enum ErrorClassification {
    RECOVERABLE,
    NON_RECOVERABLE;

    public boolean isRecoverable() {
        return this == RECOVERABLE;
    }
}
