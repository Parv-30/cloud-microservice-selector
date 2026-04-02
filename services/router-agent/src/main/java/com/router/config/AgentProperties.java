package com.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent")
@Component
public class AgentProperties {
    private String modelPath;
    private String modelConfigPath;
    private int maxLen;
    private Map<String, String> serviceUrls = new HashMap<>();
    private int downstreamTimeoutMs = 3000;
    private int downstreamRetryMax = 2;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getModelConfigPath() {
        return modelConfigPath;
    }

    public void setModelConfigPath(String modelConfigPath) {
        this.modelConfigPath = modelConfigPath;
    }

    public int getMaxLen() {
        return maxLen;
    }

    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
    }

    public Map<String, String> getServiceUrls() {
        return serviceUrls;
    }

    public void setServiceUrls(Map<String, String> serviceUrls) {
        this.serviceUrls = serviceUrls;
    }

    public int getDownstreamTimeoutMs() {
        return downstreamTimeoutMs;
    }

    public void setDownstreamTimeoutMs(int downstreamTimeoutMs) {
        this.downstreamTimeoutMs = downstreamTimeoutMs;
    }

    public int getDownstreamRetryMax() {
        return downstreamRetryMax;
    }

    public void setDownstreamRetryMax(int downstreamRetryMax) {
        this.downstreamRetryMax = downstreamRetryMax;
    }
}
