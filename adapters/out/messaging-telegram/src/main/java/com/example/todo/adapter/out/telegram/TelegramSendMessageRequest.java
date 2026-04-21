package com.example.todo.adapter.out.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramSendMessageRequest(
        @JsonProperty("chat_id") Long chatId,
        @JsonProperty("text") String text
) {
}
