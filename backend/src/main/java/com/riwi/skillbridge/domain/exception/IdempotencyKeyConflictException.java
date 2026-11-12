package com.riwi.skillbridge.domain.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) { super(message); }
}
