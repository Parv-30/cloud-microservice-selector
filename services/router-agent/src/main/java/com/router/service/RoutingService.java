package com.router.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.router.config.AgentProperties;
import com.router.exception.CircuitBreakerOpenException;
import com.router.exception.RoutingException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RoutingService {
    private final AgentProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, CircuitBreaker> breakerByOperation = new ConcurrentHashMap<>();

    public RoutingService(AgentProperties properties, RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofMillis(properties.getDownstreamTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getDownstreamTimeoutMs()))
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(100f)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
    }

    public RoutedResult route(ModelInferenceService.InferenceResult inferenceResult, String rawJson, String correlationId) {
        String label = inferenceResult.label();
        String[] parts = label.split("_", 2);
        if (parts.length != 2) {
            throw new RoutingException("Invalid model label format: " + label);
        }

        String operation = parts[0];
        String version = parts[1];
        String baseUrl = properties.getServiceUrls().get(operation);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RoutingException("Operation not mapped to service URL: " + operation);
        }

        CircuitBreaker breaker = breakerByOperation.computeIfAbsent(operation, op -> circuitBreakerRegistry.circuitBreaker(op));
        if (!breaker.tryAcquirePermission()) {
            throw new CircuitBreakerOpenException(operation);
        }

        long start = System.nanoTime();
        try {
            Object responseBody = callWithRetry(baseUrl + "/" + version, rawJson, correlationId);
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return new RoutedResult(responseBody, operation + "_" + version, operation, version);
        } catch (HttpStatusCodeException ex) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
            if (ex.getStatusCode().value() == 400) {
                String payloadOperation = extractPayloadOperation(rawJson);
                String payloadVersion = inferPayloadVersion(rawJson);
                List<RouteCandidate> fallbacks = buildFallbackCandidates(operation, version, payloadOperation, payloadVersion);

                DownstreamHttpException lastFallbackError = null;
                for (RouteCandidate candidate : fallbacks) {
                    String fallbackBaseUrl = properties.getServiceUrls().get(candidate.operation());
                    if (fallbackBaseUrl == null || fallbackBaseUrl.isBlank()) {
                        continue;
                    }
                    try {
                        Object responseBody = callWithRetry(fallbackBaseUrl + "/" + candidate.version(), rawJson, correlationId);
                        return new RoutedResult(responseBody, candidate.operation() + "_" + candidate.version(), candidate.operation(), candidate.version());
                    } catch (HttpStatusCodeException fallbackEx) {
                        lastFallbackError = new DownstreamHttpException(candidate.operation(), fallbackEx.getStatusCode().value(), fallbackEx.getResponseBodyAsString());
                        if (fallbackEx.getStatusCode().value() != 400) {
                            throw lastFallbackError;
                        }
                    }
                }

                if (lastFallbackError != null) {
                    throw lastFallbackError;
                }
            }
            throw new DownstreamHttpException(operation, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
            throw new RoutingException("Downstream connection failed for operation: " + operation, ex);
        } catch (RuntimeException ex) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, ex);
            throw ex;
        }
    }

    private Object callWithRetry(String url, String rawJson, String correlationId) {
        int attempts = 0;
        int maxAttempts = Math.max(1, properties.getDownstreamRetryMax() + 1);

        while (attempts < maxAttempts) {
            attempts++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Type", "application/json");
                headers.add("X-Correlation-Id", correlationId);
                HttpEntity<String> entity = new HttpEntity<>(rawJson, headers);

                ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
                return response.getBody();
            } catch (ResourceAccessException ex) {
                if (attempts >= maxAttempts) {
                    throw ex;
                }
            }
        }
        throw new RoutingException("Retry logic exhausted unexpectedly");
    }

    private String extractPayloadOperation(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode opNode = root.get("operation");
            if (opNode == null) {
                return null;
            }
            String value = opNode.asText();
            return (value == null || value.isBlank()) ? null : value.toLowerCase();
        } catch (Exception ex) {
            return null;
        }
    }

    private String inferPayloadVersion(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            boolean hasAB = root.hasNonNull("a") && root.hasNonNull("b");
            if (hasAB) {
                return "v1";
            }

            JsonNode numbers = root.get("numbers");
            boolean hasNumbers = numbers != null && numbers.isArray() && !numbers.isEmpty();
            if (!hasNumbers) {
                return null;
            }

            if (hasAnyMetadata(root)) {
                return "v3";
            }

            return "v2";
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean hasAnyMetadata(JsonNode root) {
        return hasNonBlankText(root, "precision")
                || hasNonBlankText(root, "label")
                || hasNonBlankText(root, "requestId")
                || hasNonBlankText(root, "currency")
                || hasNonBlankText(root, "roundingMode");
    }

    private boolean hasNonBlankText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && !node.isNull() && !node.asText("").isBlank();
    }

    private List<RouteCandidate> buildFallbackCandidates(String predictedOperation,
                                                         String predictedVersion,
                                                         String payloadOperation,
                                                         String payloadVersion) {
        Set<String> seen = new LinkedHashSet<>();
        List<RouteCandidate> candidates = new ArrayList<>();

        addCandidate(candidates, seen, payloadOperation, predictedVersion, predictedOperation, predictedVersion);
        addCandidate(candidates, seen, predictedOperation, payloadVersion, predictedOperation, predictedVersion);
        addCandidate(candidates, seen, payloadOperation, payloadVersion, predictedOperation, predictedVersion);

        return candidates;
    }

    private void addCandidate(List<RouteCandidate> candidates,
                              Set<String> seen,
                              String operation,
                              String version,
                              String predictedOperation,
                              String predictedVersion) {
        if (operation == null || operation.isBlank() || version == null || version.isBlank()) {
            return;
        }

        String normalizedOperation = operation.toLowerCase();
        String normalizedVersion = version.toLowerCase();
        if (normalizedOperation.equals(predictedOperation) && normalizedVersion.equals(predictedVersion)) {
            return;
        }

        String key = normalizedOperation + "_" + normalizedVersion;
        if (seen.add(key)) {
            candidates.add(new RouteCandidate(normalizedOperation, normalizedVersion));
        }
    }

    public record RoutedResult(Object result, String routedTo, String operation, String version) {
    }

    private record RouteCandidate(String operation, String version) {
    }

    public static class DownstreamHttpException extends RuntimeException {
        private final String service;
        private final int statusCode;
        private final String responseBody;

        public DownstreamHttpException(String service, int statusCode, String responseBody) {
            super("Downstream service returned error status " + statusCode);
            this.service = service;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public String getService() {
            return service;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
