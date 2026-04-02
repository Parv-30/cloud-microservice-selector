package com.arithmetic.multiply.controller;

import com.arithmetic.multiply.model.ArithmeticResponse;
import com.arithmetic.multiply.model.MultiplyRequestV1;
import com.arithmetic.multiply.model.MultiplyRequestV2;
import com.arithmetic.multiply.model.MultiplyRequestV3;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping
public class MultiplyController {
    @PostMapping("/v1")
    public ArithmeticResponse v1(@Valid @RequestBody MultiplyRequestV1 request) {
        requireOperation(request.getOperation());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(request.getA() * request.getB());
        response.setOperation("multiply");
        response.setVersion("v1");
        return response;
    }

    @PostMapping("/v2")
    public ArithmeticResponse v2(@Valid @RequestBody MultiplyRequestV2 request) {
        requireOperation(request.getOperation());
        double result = 1d;
        for (Double n : request.getNumbers()) {
            result *= n;
        }
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(result);
        response.setOperation("multiply");
        response.setVersion("v2");
        return response;
    }

    @PostMapping("/v3")
    public ArithmeticResponse v3(@Valid @RequestBody MultiplyRequestV3 request) {
        requireOperation(request.getOperation());
        ensureMetadataPresent(request);
        double result = 1d;
        for (Double n : request.getNumbers()) {
            result *= n;
        }
        Object output = applyPrecision(result, request.getPrecision(), request.getRoundingMode());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(output);
        response.setOperation("multiply");
        response.setVersion("v3");
        response.setLabel(request.getLabel());
        response.setRequestId(request.getRequestId());
        response.setCurrency(request.getCurrency());
        return response;
    }

    private void requireOperation(String operation) {
        if (!"multiply".equalsIgnoreCase(operation)) {
            throw new IllegalArgumentException("operation must be multiply");
        }
    }

    private void ensureMetadataPresent(MultiplyRequestV3 request) {
        boolean hasAny = notBlank(request.getPrecision()) || notBlank(request.getLabel()) || notBlank(request.getRequestId())
                || notBlank(request.getCurrency()) || notBlank(request.getRoundingMode());
        if (!hasAny) {
            throw new IllegalArgumentException("v3 requires at least one metadata field");
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Object applyPrecision(double value, String precision, String roundingMode) {
        if (notBlank(roundingMode)) {
            RoundingMode mode;
            try {
                mode = RoundingMode.valueOf(roundingMode);
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid roundingMode");
            }
            return BigDecimal.valueOf(value).setScale(2, mode).doubleValue();
        }
        if ("integer".equalsIgnoreCase(precision)) {
            return Math.round(value);
        }
        return value;
    }
}
