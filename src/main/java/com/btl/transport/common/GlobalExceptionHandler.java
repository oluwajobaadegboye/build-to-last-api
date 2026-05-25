package com.btl.transport.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        String firstMessage = fields.values().stream().findFirst().orElse("Validation failed");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", firstMessage);
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    // Static resource misses (e.g. browser requesting favicon.ico) — 404, no noise
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Client disconnected before the response was fully written — not an app error
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected mid-response: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        String msg = ex.getMessage();
        if (ex.getCause() != null) msg = msg + " | cause: " + ex.getCause().getMessage();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, msg);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "error", message,
            "status", status.value(),
            "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
