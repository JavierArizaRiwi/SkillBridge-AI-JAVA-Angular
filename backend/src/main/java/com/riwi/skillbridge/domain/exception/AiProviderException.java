package com.riwi.skillbridge.domain.exception;

/**
 * Exception thrown when an AI provider fails to generate a recommendation.
 * 
 * This exception differentiates between various failure modes:
 * - Timeout: the provider took too long to respond
 * - Unavailable: the provider is temporarily unreachable
 * - RateLimited: the rate limit quota has been exceeded
 * - InvalidInput: the input violates provider constraints
 * - InternalError: an unrecoverable error occurred
 */
public class AiProviderException extends RuntimeException {
    
    private final ErrorType errorType;
    
    public AiProviderException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }
    
    public AiProviderException(String message, ErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    public enum ErrorType {
        TIMEOUT("Timeout", "The provider took too long to respond"),
        UNAVAILABLE("Unavailable", "The provider is temporarily unreachable"),
        RATE_LIMITED("RateLimited", "The rate limit quota has been exceeded"),
        INVALID_INPUT("InvalidInput", "The input violates provider constraints"),
        INTERNAL_ERROR("InternalError", "An unrecoverable error occurred");
        
        private final String code;
        private final String description;
        
        ErrorType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
