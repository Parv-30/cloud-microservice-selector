package com.arithmetic.subtract.model;

public class ArithmeticResponse {
    private Object result;
    private String operation;
    private String version;
    private String label;
    private String requestId;
    private String currency;

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
