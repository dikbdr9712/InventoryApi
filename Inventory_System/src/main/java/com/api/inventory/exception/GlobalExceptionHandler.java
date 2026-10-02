// src/main/java/com/api/inventory/exception/GlobalExceptionHandler.java
package com.api.inventory.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every error the API sends has the same shape: { status, error, message, timestamp }. The screens show "message".
 *
 *   4xx  something the person can fix: the message says what (wrong state, not found, bad input)
 *   500  a fault on our side: the person sees a short reference number, the full details go to the server log only
 *
 * (Sign-in and permission refusals are answered first by security/SecurityExceptionAdvice.)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({OrderNotFoundException.class, ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception ex) {
        return body(HttpStatus.NOT_FOUND, "Not Found", ex instanceof NoResourceFoundException ? "That address does not exist." : ex.getMessage());
    }

    /** A seller or driver must accept the new version of their agreement first: the dashboard shows it. */
    @ExceptionHandler(TermsNotAcceptedException.class)
    public ResponseEntity<Map<String, Object>> termsRequired(TermsNotAcceptedException ex) {
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.PRECONDITION_REQUIRED, "Terms Required", ex.getMessage());
        response.getBody().put("code", "TERMS_REQUIRED");
        response.getBody().put("termsType", ex.getTermsType());
        response.getBody().put("termsVersion", ex.getVersion());
        return response;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid Order State", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Some of the information sent was missing or in the wrong format.");
    }

    /** Sent in the wrong format (for example plain JSON where a form with files is expected). */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> wrongFormat(Exception ex) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Format", "The information was sent in the wrong format.");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> wrongMethod(Exception ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "This action is not available here.");
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<Map<String, Object>> missingPart(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Part of the form is missing. Please fill it in again.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooBig(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "Too Large", "The file is too big.");
    }

    /** Two people saving the same thing at once, or a duplicate that the database refused. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException ex) {
        log.warn("Database refused a change: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "Conflict", "This could not be saved because it clashes with existing data. Please reload and try again.");
    }

    /**
     * A plain RuntimeException("...") in this code base is a deliberate message for the person (for example
     * "Item not found"). Anything else (a NullPointerException, a database outage) is a fault: log it, hide it.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> runtime(RuntimeException ex) {
        if (ex.getClass() == RuntimeException.class && ex.getMessage() != null && ex.getCause() == null) {
            return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        }
        return fault(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> fault(Exception ex) {
        String ref = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Unexpected error, reference {}", ref, ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error",
                "Something went wrong on our side. Please try again. If it keeps happening, tell us this reference: " + ref);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
