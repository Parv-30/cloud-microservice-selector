package com.arithmetic.divide.controller;

import com.arithmetic.divide.model.ArithmeticResponse;
import com.arithmetic.divide.model.DivideRequestV1;
import com.arithmetic.divide.model.DivideRequestV2;
import com.arithmetic.divide.model.DivideRequestV3;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping
public class DivideController {
    @PostMapping("/v1")
    public ArithmeticResponse v1(@Valid @RequestBody DivideRequestV1 request) {
        requireOperation(request.getOperation());
        requireNonZero(request.getB());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(request.getA() / request.getB());
        response.setOperation("divide");
        response.setVersion("v1");
        return response;
    }

    @PostMapping("/v2")
    public ArithmeticResponse v2(@Valid @RequestBody DivideRequestV2 request) {
        requireOperation(request.getOperation());
        double denominator = 1d;
        for (int i = 1; i < request.getNumbers().size(); i++) {
            double divisor = request.getNumbers().get(i);
            requireNonZero(divisor);
            denominator *= divisor;
        }
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(request.getNumbers().get(0) / denominator);
        response.setOperation("divide");
        response.setVersion("v2");
        return response;
    }

    @PostMapping("/v3")
    public ArithmeticResponse v3(@Valid @RequestBody DivideRequestV3 request) {
        requireOperation(request.getOperation());
        ensureMetadataPresent(request);
        double denominator = 1d;
        for (int i = 1; i < request.getNumbers().size(); i++) {
            double divisor = request.getNumbers().get(i);
            requireNonZero(divisor);
            denominator *= divisor;
        }
        double result = request.getNumbers().get(0) / denominator;
        Object output = applyPrecision(result, request.getPrecision(), request.getRoundingMode());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(output);
        response.setOperation("divide");
        response.setVersion("v3");
        response.setLabel(request.getLabel());
        response.setRequestId(request.getRequestId());
        response.setCurrency(request.getCurrency());
        return response;
    }

    private void requireOperation(String operation) {
        if (!"divide".equalsIgnoreCase(operation)) {
            throw new IllegalArgumentException("operation must be divide");
        }
    }

    private void requireNonZero(Double value) {
        if (value == null || value == 0d) {
            throw new ArithmeticException("divide by zero");
        }
    }

    private void ensureMetadataPresent(DivideRequestV3 request) {
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
