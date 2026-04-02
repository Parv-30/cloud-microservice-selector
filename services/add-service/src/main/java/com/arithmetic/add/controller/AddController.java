package com.arithmetic.add.controller;

import com.arithmetic.add.model.AddRequestV1;
import com.arithmetic.add.model.AddRequestV2;
import com.arithmetic.add.model.AddRequestV3;
import com.arithmetic.add.model.ArithmeticResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping
public class AddController {
    @PostMapping("/v1")
    public ArithmeticResponse v1(@Valid @RequestBody AddRequestV1 request) {
        requireOperation(request.getOperation());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(request.getA() + request.getB());
        response.setOperation("add");
        response.setVersion("v1");
        return response;
    }

    @PostMapping("/v2")
    public ArithmeticResponse v2(@Valid @RequestBody AddRequestV2 request) {
        requireOperation(request.getOperation());
        double total = request.getNumbers().stream().mapToDouble(Double::doubleValue).sum();
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(total);
        response.setOperation("add");
        response.setVersion("v2");
        return response;
    }

    @PostMapping("/v3")
    public ArithmeticResponse v3(@Valid @RequestBody AddRequestV3 request) {
        requireOperation(request.getOperation());
        ensureMetadataPresent(request);

        double total = request.getNumbers().stream().mapToDouble(Double::doubleValue).sum();
        Object result = applyPrecision(total, request.getPrecision(), request.getRoundingMode());

        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(result);
        response.setOperation("add");
        response.setVersion("v3");
        response.setLabel(request.getLabel());
        response.setRequestId(request.getRequestId());
        response.setCurrency(request.getCurrency());
        return response;
    }

    private void requireOperation(String operation) {
        if (!"add".equalsIgnoreCase(operation)) {
            throw new IllegalArgumentException("operation must be add");
        }
    }

    private void ensureMetadataPresent(AddRequestV3 request) {
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
