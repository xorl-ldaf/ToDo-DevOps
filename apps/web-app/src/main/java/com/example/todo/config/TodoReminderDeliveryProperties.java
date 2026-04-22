package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "todo.reminder-delivery")
public class TodoReminderDeliveryProperties {
    private boolean enabled;
    private int batchSize = 25;
    private int maxAttempts = 3;
    private Duration retryBackoff = Duration.ofSeconds(30);
    private Duration processingTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public int requireBatchSize() {
        if (batchSize < 1) {
            throw new IllegalStateException("todo.reminder-delivery.batch-size must be at least 1");
        }
        return batchSize;
    }

    public int requireMaxAttempts() {
        if (maxAttempts < 1) {
            throw new IllegalStateException("todo.reminder-delivery.max-attempts must be at least 1");
        }
        return maxAttempts;
    }

    public Duration requireRetryBackoff() {
        return requirePositiveDuration(retryBackoff, "retry-backoff");
    }

    public Duration requireProcessingTimeout() {
        return requirePositiveDuration(processingTimeout, "processing-timeout");
    }

    private Duration requirePositiveDuration(Duration value, String fieldName) {
        Duration actualValue = value == null ? null : value;
        if (actualValue == null || actualValue.isNegative() || actualValue.isZero()) {
            throw new IllegalStateException("todo.reminder-delivery." + fieldName + " must be a positive duration");
        }
        return actualValue;
    }
}
