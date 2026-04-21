package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "todo.telegram")
public class TodoTelegramProperties {
    private boolean enabled = false;
    private String baseUrl = "https://api.telegram.org";
    private String botToken;

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

    public String requireBotToken() {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("todo.telegram.bot-token must be set when Telegram delivery is enabled");
        }
        return botToken;
    }
}
