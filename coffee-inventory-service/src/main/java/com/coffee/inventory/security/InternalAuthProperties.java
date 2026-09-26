package com.coffee.inventory.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "coffee.internal-auth")
public class InternalAuthProperties {
    private boolean enabled = true;
    private long clockSkewSeconds = 300;
    private int maxBodyBytes = 1_048_576;
    private String serviceName = "coffee-inventory-service";
    private String replayStore = "in-memory";
    private Map<String, String> keys = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(long clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }
    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getReplayStore() { return replayStore; }
    public void setReplayStore(String replayStore) { this.replayStore = replayStore; }
    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys == null ? new HashMap<>() : keys; }
}
