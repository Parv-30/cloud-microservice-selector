package com.arithmetic.divide.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<Map<String, Object>> divideByZero(ArithmeticException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "DIVIDE_BY_ZERO", "divide", null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_INPUT", "divide", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", "INTERNAL_ERROR", "divide", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String code, String operation, String version) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);
        body.put("code", code);
        body.put("operation", operation);
        body.put("version", version);
        return ResponseEntity.status(status).body(body);
    }
}
