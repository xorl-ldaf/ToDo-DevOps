package com.example.todo.domain.shared;

import com.example.todo.domain.reminder.ReminderId;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueObjectTest {

    @Test
    void identifiersShouldRejectNullValues() {
        IllegalArgumentException taskIdException = assertThrows(
                IllegalArgumentException.class,
                () -> new TaskId(null)
        );
        IllegalArgumentException reminderIdException = assertThrows(
                IllegalArgumentException.class,
                () -> new ReminderId(null)
        );
        IllegalArgumentException userIdException = assertThrows(
                IllegalArgumentException.class,
                () -> new UserId(null)
        );

        assertEquals("task id must not be null", taskIdException.getMessage());
        assertEquals("reminder id must not be null", reminderIdException.getMessage());
        assertEquals("user id must not be null", userIdException.getMessage());
    }

    @Test
    void telegramChatIdShouldRejectNullAndNonPositiveValues() {
        IllegalArgumentException nullException = assertThrows(
                IllegalArgumentException.class,
                () -> new TelegramChatId(null)
        );
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new TelegramChatId(0L)
        );

        assertEquals("telegram chat id must be positive", nullException.getMessage());
        assertEquals("telegram chat id must be positive", zeroException.getMessage());
    }
}
