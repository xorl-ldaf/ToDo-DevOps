package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "todo.kafka")
public class TodoKafkaProperties {
    private boolean enabled;
    private String bootstrapServers = "localhost:9092";
    private String consumerGroupId = "todo-web-app-reminder-scheduled-v1";
    private final Topics topics = new Topics();

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

    public static final class Topics {
        private String reminderScheduledV1 = "todo.reminder.scheduled.v1";

        public String getReminderScheduledV1() {
            return reminderScheduledV1;
        }

        public void setReminderScheduledV1(String reminderScheduledV1) {
            this.reminderScheduledV1 = reminderScheduledV1;
        }
    }
}
