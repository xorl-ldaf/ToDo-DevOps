package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "todo.reminder-delivery")
public class TodoReminderDeliveryProperties {
    private boolean enabled;
    private int batchSize = 25;

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

    public int requireBatchSize() {
        if (batchSize < 1) {
            throw new IllegalStateException("todo.reminder-delivery.batch-size must be at least 1");
        }
        return batchSize;
    }
}
