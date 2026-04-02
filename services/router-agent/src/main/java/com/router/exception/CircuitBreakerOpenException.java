package com.router.exception;

public class CircuitBreakerOpenException extends RuntimeException {
    private final String service;

    public CircuitBreakerOpenException(String service) {
        super("downstream unavailable");
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
