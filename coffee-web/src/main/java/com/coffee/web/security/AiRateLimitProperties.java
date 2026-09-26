package com.coffee.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime limits for AI endpoints. Values are environment configurable and shared by all web nodes when Redis is enabled. */
@Component
@ConfigurationProperties(prefix = "coffee.ai.rate-limit")
public class AiRateLimitProperties {
    private boolean enabled = true;
    private boolean redisEnabled = true;
    private int windowSeconds = 60;
    private int businessRequests = 30;
    private int customerPlanRequests = 20;
    private int customerConfirmRequests = 10;
    private int merchantRequests = 30;
    private int evaluationRequests = 5;
    private int localMaxEntries = 10_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRedisEnabled() { return redisEnabled; }
    public void setRedisEnabled(boolean redisEnabled) { this.redisEnabled = redisEnabled; }
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    public int getBusinessRequests() { return businessRequests; }
    public void setBusinessRequests(int businessRequests) { this.businessRequests = businessRequests; }
    public int getCustomerPlanRequests() { return customerPlanRequests; }
    public void setCustomerPlanRequests(int customerPlanRequests) { this.customerPlanRequests = customerPlanRequests; }
    public int getCustomerConfirmRequests() { return customerConfirmRequests; }
    public void setCustomerConfirmRequests(int customerConfirmRequests) { this.customerConfirmRequests = customerConfirmRequests; }
    public int getMerchantRequests() { return merchantRequests; }
    public void setMerchantRequests(int merchantRequests) { this.merchantRequests = merchantRequests; }
    public int getEvaluationRequests() { return evaluationRequests; }
    public void setEvaluationRequests(int evaluationRequests) { this.evaluationRequests = evaluationRequests; }
    public int getLocalMaxEntries() { return localMaxEntries; }
    public void setLocalMaxEntries(int localMaxEntries) { this.localMaxEntries = localMaxEntries; }
}
