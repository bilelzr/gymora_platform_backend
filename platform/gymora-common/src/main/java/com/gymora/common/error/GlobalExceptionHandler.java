package com.gymora.common.error;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 problem responses with a stable {@code code} and a message in the
 * caller's language (from the Accept-Language header).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(GymoraException.class)
    ResponseEntity<ProblemDetail> handleGymora(GymoraException ex) {
        if (ex.getStatus().is5xxServerError()) {
            // Our side or a dependency failed (e.g. Keycloak unreachable): keep the cause in the logs.
            log.error("{} ({})", ex.getCode(), ex.getStatus(), ex);
        }
        return problem(ex.getStatus(), ex.getCode(), ex.getArgs());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "error.access_denied");
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrentEdit(Exception ex) {
        return problem(HttpStatus.CONFLICT, "error.concurrent_update");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "error.conflict");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), messageFor(error, locale)))
                .toList();
        ProblemDetail body = baseProblem(HttpStatus.BAD_REQUEST, "error.validation");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private String messageFor(FieldError error, Locale locale) {
        return messages.getMessage(error, locale);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, Object... args) {
        return ResponseEntity.status(status).body(baseProblem(status, code, args));
    }

    private ProblemDetail baseProblem(HttpStatus status, String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, messages.getMessage(code, args, code, locale));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    public record FieldViolation(String field, String message) {
    }
}
