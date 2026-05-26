package com.example.todo.application.policy;

import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.domain.user.User;

public class ReminderFailureReasonPolicy {
    private static final String TASK_MISSING = "task no longer exists";
    private static final String ASSIGNEE_MISSING = "assignee no longer exists";
    private static final String NO_TELEGRAM_CHAT_ID = "recipient has no telegram chat id";

    public String taskMissing() {
        return TASK_MISSING;
    }

    public String assigneeMissing() {
        return ASSIGNEE_MISSING;
    }

    public boolean hasNoTelegramChatId(User recipient) {
        User actualRecipient = requireNonNull(recipient, "recipient");
        return actualRecipient.getTelegramChatId() == null;
    }

    public String noTelegramChatId() {
        return NO_TELEGRAM_CHAT_ID;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new ApplicationValidationException(fieldName + " must not be null");
        }
        return value;
    }
}
