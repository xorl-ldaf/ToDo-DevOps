package com.example.todo.domain.reminder;

import com.example.todo.domain.shared.exception.DomainValidationException;
import com.example.todo.domain.shared.exception.InvalidStateTransitionException;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReminderTest {

    @Test
    void scheduleShouldCreatePendingReminderWithConsistentTimestamps() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T11:00:00Z");

        Reminder reminder = Reminder.schedule(TaskId.newId(), remindAt, now);

        assertEquals(ReminderStatus.PENDING, reminder.getStatus());
        assertEquals(remindAt, reminder.getRemindAt());
        assertEquals(now, reminder.getCreatedAt());
        assertEquals(now, reminder.getUpdatedAt());
        assertNull(reminder.getSentAt());
    }

    @Test
    void scheduleShouldRejectReminderTimeInThePast() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Instant remindAt = Instant.parse("2026-04-19T09:59:59Z");

        DomainValidationException exception = assertThrows(
                DomainValidationException.class,
                () -> Reminder.schedule(TaskId.newId(), remindAt, now)
        );

        assertEquals("remindAt must not be in the past", exception.getMessage());
    }

    @Test
    void isDueAtShouldReturnTrueOnlyForPendingDueReminder() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now, now);

        assertTrue(reminder.isDueAt(now));
        assertTrue(reminder.isDueAt(now.plusSeconds(60)));

        reminder.markPublished(now.plusSeconds(1));

        assertFalse(reminder.isDueAt(now.plusSeconds(60)));
    }

    @Test
    void markPublishedShouldMoveReminderFromPendingToPublished() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(300), now);

        Instant publishedAt = now.plusSeconds(120);
        reminder.markPublished(publishedAt);

        assertEquals(ReminderStatus.PUBLISHED, reminder.getStatus());
        assertEquals(publishedAt, reminder.getUpdatedAt());
        assertNull(reminder.getSentAt());
    }

    @Test
    void markPublishedShouldRejectIllegalTransition() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(300), now);

        reminder.markPublished(now.plusSeconds(60));

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> reminder.markPublished(now.plusSeconds(120))
        );

        assertEquals(
                "reminder cannot be published from status: PUBLISHED",
                exception.getMessage()
        );
    }

    @Test
    void markSentShouldMoveReminderFromPublishedToSent() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(300), now);

        Instant publishedAt = now.plusSeconds(60);
        Instant sentAt = now.plusSeconds(120);

        reminder.markPublished(publishedAt);
        reminder.markSent(sentAt);

        assertEquals(ReminderStatus.SENT, reminder.getStatus());
        assertEquals(sentAt, reminder.getSentAt());
        assertEquals(sentAt, reminder.getUpdatedAt());
    }

    @Test
    void markSentShouldRejectIllegalTransitionFromPending() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        Reminder reminder = Reminder.schedule(TaskId.newId(), now.plusSeconds(300), now);

        InvalidStateTransitionException exception = assertThrows(
                InvalidStateTransitionException.class,
                () -> reminder.markSent(now.plusSeconds(60))
        );

        assertEquals(
                "reminder cannot be marked as sent from status: PENDING",
                exception.getMessage()
        );
    }
}