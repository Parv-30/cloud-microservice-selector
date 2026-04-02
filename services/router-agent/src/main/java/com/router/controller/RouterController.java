package com.router.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.router.model.RouteResponse;
import com.router.service.ModelInferenceService;
import com.router.service.RoutingService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
public class RouterController {
    private final ModelInferenceService modelInferenceService;
    private final RoutingService routingService;
    private final ObjectMapper objectMapper;

    public RouterController(ModelInferenceService modelInferenceService,
                            RoutingService routingService,
                            ObjectMapper objectMapper) {
        this.modelInferenceService = modelInferenceService;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/route")
    public ResponseEntity<RouteResponse> route(@RequestBody String rawJson,
                                               @RequestHeader(value = "X-Correlation-Id", required = false) String incomingCorrelationId) {
        String correlationId = incomingCorrelationId == null || incomingCorrelationId.isBlank()
                ? UUID.randomUUID().toString()
                : incomingCorrelationId;

        MDC.put("correlationId", correlationId);
        try {
            JsonNode payload = objectMapper.readTree(rawJson);
            JsonNode operation = payload.get("operation");
            if (operation == null || operation.asText().isBlank()) {
                throw new IllegalArgumentException("invalid/missing operation field in payload");
            }

            ModelInferenceService.InferenceResult inference = modelInferenceService.predictLabel(rawJson);
            RoutingService.RoutedResult routed = routingService.route(inference, rawJson, correlationId);

            RouteResponse response = new RouteResponse();
            response.setResult(routed.result());
            response.setRoutedTo(routed.routedTo());
            response.setOperation(routed.operation());
            response.setVersion(routed.version());
            response.setDecisionSource("primary_model");
            response.setConfidence(inference.confidence());
            response.setCorrelationId(correlationId);
            return ResponseEntity.ok(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid JSON payload", ex);
        } finally {
            MDC.clear();
        }
    }
}
