package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "todo.telegram")
public class TodoTelegramProperties {
    private boolean enabled = false;
    private String baseUrl = "https://api.telegram.org";
    private String botToken;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int maxAttempts = 3;
    private Duration retryBackoff = Duration.ofSeconds(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
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

    public String requireBotToken() {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("todo.telegram.bot-token must be set when Telegram delivery is enabled");
        }
        return botToken;
    }

    public Duration requireConnectTimeout() {
        return requirePositiveDuration(connectTimeout, "connectTimeout");
    }

    public Duration requireReadTimeout() {
        return requirePositiveDuration(readTimeout, "readTimeout");
    }

    public int requireMaxAttempts() {
        if (maxAttempts < 1) {
            throw new IllegalStateException("todo.telegram.max-attempts must be at least 1");
        }
        return maxAttempts;
    }

    public Duration requireRetryBackoff() {
        return requirePositiveDuration(retryBackoff, "retryBackoff");
    }

    private Duration requirePositiveDuration(Duration value, String fieldName) {
        Duration actualValue = value == null ? null : value;
        if (actualValue == null || actualValue.isNegative() || actualValue.isZero()) {
            throw new IllegalStateException("todo.telegram." + fieldName + " must be a positive duration");
        }
        return actualValue;
    }
}
