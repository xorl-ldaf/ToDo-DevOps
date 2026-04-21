package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "todo.kafka")
public class TodoKafkaProperties {
    private boolean enabled;
    private String bootstrapServers = "localhost:9092";
    private String consumerGroupId = "todo-web-app-reminder-scheduled-v1";
    private final Topics topics = new Topics();
    private final Producer producer = new Producer();
    private final Consumer consumer = new Consumer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public void setConsumerGroupId(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public Topics getTopics() {
        return topics;
    }

    public Producer getProducer() {
        return producer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public static final class Topics {
        private String reminderScheduledV1 = "todo.reminder.scheduled.v1";

        public String getReminderScheduledV1() {
            return reminderScheduledV1;
        }

        public void setReminderScheduledV1(String reminderScheduledV1) {
            this.reminderScheduledV1 = reminderScheduledV1;
        }
    }

    public static final class Producer {
        private int retries = 3;
        private Duration retryBackoff = Duration.ofSeconds(1);
        private Duration requestTimeout = Duration.ofSeconds(5);
        private Duration deliveryTimeout = Duration.ofSeconds(20);

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public Duration getDeliveryTimeout() {
            return deliveryTimeout;
        }

        public void setDeliveryTimeout(Duration deliveryTimeout) {
            this.deliveryTimeout = deliveryTimeout;
        }
    }

    public static final class Consumer {
        private long maxAttempts = 2;
        private Duration retryBackoff = Duration.ofSeconds(1);

        public long getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(long maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }
}
