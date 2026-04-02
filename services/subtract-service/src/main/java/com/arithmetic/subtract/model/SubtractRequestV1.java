package com.arithmetic.subtract.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SubtractRequestV1 {
    @NotBlank
    private String operation;
    @NotNull
    private Double a;
    @NotNull
    private Double b;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public Double getA() { return a; }
    public void setA(Double a) { this.a = a; }
    public Double getB() { return b; }
    public void setB(Double b) { this.b = b; }
}
