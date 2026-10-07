package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
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
}
