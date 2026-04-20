package com.example.todo.application.service;

import com.example.todo.application.port.out.LoadDueRemindersPort;
import com.example.todo.application.port.out.PublishReminderEventPort;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.task.TaskId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanDueRemindersServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private LoadDueRemindersPort loadDueRemindersPort;

    @Mock
    private PublishReminderEventPort publishReminderEventPort;

    @Mock
    private SaveReminderPort saveReminderPort;

    private ScanDueRemindersService service;

    @BeforeEach
    void setUp() {
        service = new ScanDueRemindersService(loadDueRemindersPort, publishReminderEventPort, saveReminderPort);
    }

    @Test
    void scanAndPublishDueRemindersShouldPublishAndPersistOnlyDuePendingReminders() {
        Reminder firstDueReminder = pendingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", NOW.minusSeconds(60));
        Reminder secondDueReminder = pendingReminder("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", NOW);
        Reminder futureReminder = pendingReminder("cccccccc-cccc-cccc-cccc-cccccccccccc", NOW.plusSeconds(60));
        Reminder alreadyPublishedReminder = Reminder.restore(
                reminderId("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                taskId("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                NOW.minusSeconds(30),
                ReminderStatus.PUBLISHED,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                null
        );
        when(loadDueRemindersPort.loadDueReminders(NOW)).thenReturn(
                List.of(firstDueReminder, secondDueReminder, futureReminder, alreadyPublishedReminder)
        );
        when(saveReminderPort.save(any(Reminder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<ReminderStatus> statusesAtPublish = new ArrayList<>();
        doAnswer(invocation -> {
            Reminder reminder = invocation.getArgument(0);
            statusesAtPublish.add(reminder.getStatus());
            return null;
        }).when(publishReminderEventPort).publish(any(Reminder.class));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        ArgumentCaptor<Reminder> savedReminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        InOrder inOrder = inOrder(loadDueRemindersPort, publishReminderEventPort, saveReminderPort);
        inOrder.verify(loadDueRemindersPort).loadDueReminders(NOW);
        inOrder.verify(publishReminderEventPort).publish(firstDueReminder);
        inOrder.verify(saveReminderPort).save(firstDueReminder);
        inOrder.verify(publishReminderEventPort).publish(secondDueReminder);
        inOrder.verify(saveReminderPort).save(secondDueReminder);
        verify(saveReminderPort, times(2)).save(savedReminderCaptor.capture());
        verifyNoMoreInteractions(loadDueRemindersPort, publishReminderEventPort, saveReminderPort);

        assertEquals(2, publishedCount);
        assertEquals(List.of(ReminderStatus.PENDING, ReminderStatus.PENDING), statusesAtPublish);
        assertEquals(ReminderStatus.PUBLISHED, firstDueReminder.getStatus());
        assertEquals(ReminderStatus.PUBLISHED, secondDueReminder.getStatus());
        assertEquals(NOW, firstDueReminder.getUpdatedAt());
        assertEquals(NOW, secondDueReminder.getUpdatedAt());
        assertEquals(List.of(firstDueReminder, secondDueReminder), savedReminderCaptor.getAllValues());
    }

    @Test
    void scanAndPublishDueRemindersShouldValidateNow() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.scanAndPublishDueReminders(null)
        );

        assertEquals("now must not be null", exception.getMessage());
        verifyNoInteractions(loadDueRemindersPort, publishReminderEventPort, saveReminderPort);
    }

    @Test
    void scanAndPublishDueRemindersShouldReturnZeroWhenNothingIsDue() {
        Reminder futureReminder = pendingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", NOW.plusSeconds(60));
        when(loadDueRemindersPort.loadDueReminders(NOW)).thenReturn(List.of(futureReminder));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        assertEquals(0, publishedCount);
        verify(loadDueRemindersPort).loadDueReminders(NOW);
        verifyNoMoreInteractions(loadDueRemindersPort);
        verifyNoInteractions(publishReminderEventPort, saveReminderPort);
    }

    private Reminder pendingReminder(String reminderId, Instant remindAt) {
        return Reminder.restore(
                reminderId(reminderId),
                taskId("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                remindAt,
                ReminderStatus.PENDING,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600),
                null
        );
    }

    private com.example.todo.domain.reminder.ReminderId reminderId(String value) {
        return new com.example.todo.domain.reminder.ReminderId(UUID.fromString(value));
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }
}
