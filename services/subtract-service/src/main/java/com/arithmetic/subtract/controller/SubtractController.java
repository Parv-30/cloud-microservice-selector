package com.arithmetic.subtract.controller;

import com.arithmetic.subtract.model.ArithmeticResponse;
import com.arithmetic.subtract.model.SubtractRequestV1;
import com.arithmetic.subtract.model.SubtractRequestV2;
import com.arithmetic.subtract.model.SubtractRequestV3;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping
public class SubtractController {
    @PostMapping("/v1")
    public ArithmeticResponse v1(@Valid @RequestBody SubtractRequestV1 request) {
        requireOperation(request.getOperation());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(request.getA() - request.getB());
        response.setOperation("subtract");
        response.setVersion("v1");
        return response;
    }

    @PostMapping("/v2")
    public ArithmeticResponse v2(@Valid @RequestBody SubtractRequestV2 request) {
        requireOperation(request.getOperation());
        double result = request.getNumbers().get(0);
        for (int i = 1; i < request.getNumbers().size(); i++) {
            result -= request.getNumbers().get(i);
        }
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(result);
        response.setOperation("subtract");
        response.setVersion("v2");
        return response;
    }

    @PostMapping("/v3")
    public ArithmeticResponse v3(@Valid @RequestBody SubtractRequestV3 request) {
        requireOperation(request.getOperation());
        ensureMetadataPresent(request);
        double result = request.getNumbers().get(0);
        for (int i = 1; i < request.getNumbers().size(); i++) {
            result -= request.getNumbers().get(i);
        }
        Object output = applyPrecision(result, request.getPrecision(), request.getRoundingMode());
        ArithmeticResponse response = new ArithmeticResponse();
        response.setResult(output);
        response.setOperation("subtract");
        response.setVersion("v3");
        response.setLabel(request.getLabel());
        response.setRequestId(request.getRequestId());
        response.setCurrency(request.getCurrency());
        return response;
    }

    private void requireOperation(String operation) {
        if (!"subtract".equalsIgnoreCase(operation)) {
            throw new IllegalArgumentException("operation must be subtract");
        }
    }

    private void ensureMetadataPresent(SubtractRequestV3 request) {
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
