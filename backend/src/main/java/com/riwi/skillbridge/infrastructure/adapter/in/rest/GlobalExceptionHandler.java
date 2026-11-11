package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.exception.InvalidMediaFileException;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.IdempotencyKeyConflictException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(DomainNotFoundException.class)
    ProblemDetail notFound(DomainNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Resource not found");
        return p;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail business(BusinessRuleException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Business rule violation");
        return p;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        p.setTitle("Unauthorized");
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Solicitud inválida");
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        p.setTitle("Validation error");
        return p;
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail forbidden(ForbiddenOperationException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        p.setTitle("Forbidden");
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        p.setTitle("Invalid request");
        return p;
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ProblemDetail fileTooLarge(MaxUploadSizeExceededException ex) { ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"El archivo supera el tamaño máximo permitido"); p.setTitle("Archivo demasiado grande"); return p; }
    @ExceptionHandler(InvalidMediaFileException.class) ProblemDetail media(InvalidMediaFileException ex) { ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage()); p.setTitle("Archivo inválido"); return p; }
    @ExceptionHandler(AiProviderException.class)
    ProblemDetail ai(AiProviderException ex) {
        String correlationId = CorrelationIdHolder.get();
        log.error("AI request failed type={} correlationId={}", ex.getErrorType(), correlationId, ex);
        HttpStatus status = switch (ex.getErrorType()) {
            case INVALID_INPUT -> HttpStatus.BAD_GATEWAY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        String detail = switch (ex.getErrorType()) {
            case INVALID_INPUT -> "El servicio de IA rechazó la configuración o el modelo solicitado.";
            case RATE_LIMITED -> "El servicio de IA alcanzó su cuota. Inténtalo más tarde.";
            case TIMEOUT -> "El servicio de IA tardó demasiado en responder. Inténtalo de nuevo.";
            case UNAVAILABLE -> "No fue posible conectar con el servicio de IA. Inténtalo más tarde.";
            case INTERNAL_ERROR -> "El servicio de IA devolvió una respuesta que no se pudo procesar.";
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle("No se pudo generar la recomendación");
        if (correlationId != null) p.setProperty("correlationId", correlationId);
        p.setProperty("code", ex.getErrorType().getCode());
        return p;
    }
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyKeyConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
