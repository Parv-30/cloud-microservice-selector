package com.arithmetic.divide.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class DivideRequestV2 {
    @NotBlank
    private String operation;
    @NotEmpty
    @Size(min = 2)
    private List<Double> numbers;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public List<Double> getNumbers() { return numbers; }
    public void setNumbers(List<Double> numbers) { this.numbers = numbers; }
}
