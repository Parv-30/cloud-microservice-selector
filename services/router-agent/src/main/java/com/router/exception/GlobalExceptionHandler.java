package com.router.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.router.service.RoutingService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RoutingException.class)
    public ResponseEntity<Map<String, Object>> handleRouting(RoutingException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), "ROUTING_FAILED", null);
    }

    @ExceptionHandler(InferenceException.class)
    public ResponseEntity<Map<String, Object>> handleInference(InferenceException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), "INFERENCE_ERROR", null);
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<Map<String, Object>> handleOpenCircuit(CircuitBreakerOpenException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "downstream unavailable", "DOWNSTREAM_UNAVAILABLE", ex.getService());
    }

    @ExceptionHandler(RoutingService.DownstreamHttpException.class)
    public ResponseEntity<Map<String, Object>> handleDownstreamHttp(RoutingService.DownstreamHttpException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return build(status, ex.getResponseBody(), "ROUTING_FAILED", ex.getService());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_REQUEST", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", "INTERNAL_ERROR", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, String code, String service) {
        Map<String, Object> body = new HashMap<>();
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        body.put("error", message);
        body.put("code", code);
        body.put("correlationId", correlationId);
        if (service != null) {
            body.put("service", service);
        }
        return ResponseEntity.status(status).body(body);
    }
}
