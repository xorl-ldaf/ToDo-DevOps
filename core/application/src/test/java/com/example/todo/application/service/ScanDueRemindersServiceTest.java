package com.example.todo.application.service;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.LoadDueRemindersPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.application.port.out.SaveReminderPort;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderStatus;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
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
    private LoadTaskPort loadTaskPort;

    @Mock
    private LoadUserDetailsPort loadUserDetailsPort;

    @Mock
    private DeliverReminderNotificationPort deliverReminderNotificationPort;

    @Mock
    private SaveReminderPort saveReminderPort;

    private ScanDueRemindersService service;

    @BeforeEach
    void setUp() {
        service = new ScanDueRemindersService(
                loadDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                saveReminderPort
        );
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
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(loadTaskPort.loadById(task.getId())).thenReturn(java.util.Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(java.util.Optional.of(recipient));
        List<ReminderStatus> statusesAtDelivery = new ArrayList<>();
        doAnswer(invocation -> {
            ReminderNotificationV1 notification = invocation.getArgument(0);
            statusesAtDelivery.add(firstDueReminder.getId().equals(new com.example.todo.domain.reminder.ReminderId(notification.reminderId()))
                    ? firstDueReminder.getStatus()
                    : secondDueReminder.getStatus());
            return ReminderNotificationDeliveryResult.delivered();
        }).when(deliverReminderNotificationPort).deliver(any(ReminderNotificationV1.class));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        ArgumentCaptor<Reminder> savedReminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        ArgumentCaptor<ReminderNotificationV1> notificationCaptor = ArgumentCaptor.forClass(ReminderNotificationV1.class);
        InOrder inOrder = inOrder(
                loadDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                saveReminderPort
        );
        inOrder.verify(loadDueRemindersPort).loadDueReminders(NOW);
        inOrder.verify(loadTaskPort).loadById(firstDueReminder.getTaskId());
        inOrder.verify(loadUserDetailsPort).loadById(task.getAssigneeId());
        inOrder.verify(deliverReminderNotificationPort).deliver(notificationCaptor.capture());
        inOrder.verify(saveReminderPort).save(firstDueReminder);
        inOrder.verify(loadTaskPort).loadById(secondDueReminder.getTaskId());
        inOrder.verify(loadUserDetailsPort).loadById(task.getAssigneeId());
        inOrder.verify(deliverReminderNotificationPort).deliver(notificationCaptor.capture());
        inOrder.verify(saveReminderPort).save(secondDueReminder);
        verify(saveReminderPort, times(2)).save(savedReminderCaptor.capture());
        verifyNoMoreInteractions(
                loadDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                saveReminderPort
        );

        assertEquals(2, publishedCount);
        assertEquals(List.of(ReminderStatus.PENDING, ReminderStatus.PENDING), statusesAtDelivery);
        assertEquals(ReminderStatus.PUBLISHED, firstDueReminder.getStatus());
        assertEquals(ReminderStatus.PUBLISHED, secondDueReminder.getStatus());
        assertEquals(NOW, firstDueReminder.getUpdatedAt());
        assertEquals(NOW, secondDueReminder.getUpdatedAt());
        assertEquals(List.of(firstDueReminder, secondDueReminder), savedReminderCaptor.getAllValues());
        assertEquals(2, notificationCaptor.getAllValues().size());
        assertEquals(task.getId().value(), notificationCaptor.getAllValues().get(0).taskId());
        assertEquals(recipient.getId().value(), notificationCaptor.getAllValues().get(0).recipientUserId());
        assertEquals(recipient.getTelegramChatId().value(), notificationCaptor.getAllValues().get(0).recipientTelegramChatId());
    }

    @Test
    void scanAndPublishDueRemindersShouldValidateNow() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.scanAndPublishDueReminders(null)
        );

        assertEquals("now must not be null", exception.getMessage());
        verifyNoInteractions(
                loadDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                saveReminderPort
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldReturnZeroWhenNothingIsDue() {
        Reminder futureReminder = pendingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", NOW.plusSeconds(60));
        when(loadDueRemindersPort.loadDueReminders(NOW)).thenReturn(List.of(futureReminder));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        assertEquals(0, publishedCount);
        verify(loadDueRemindersPort).loadDueReminders(NOW);
        verifyNoMoreInteractions(loadDueRemindersPort);
        verifyNoInteractions(loadTaskPort, loadUserDetailsPort, deliverReminderNotificationPort, saveReminderPort);
    }

    @Test
    void scanAndPublishDueRemindersShouldSkipWhenRecipientHasNoTelegramChat() {
        Reminder dueReminder = pendingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", NOW.minusSeconds(60));
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", null);
        when(loadDueRemindersPort.loadDueReminders(NOW)).thenReturn(List.of(dueReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(java.util.Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(java.util.Optional.of(recipient));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        assertEquals(0, publishedCount);
        verify(loadDueRemindersPort).loadDueReminders(NOW);
        verify(loadTaskPort).loadById(task.getId());
        verify(loadUserDetailsPort).loadById(task.getAssigneeId());
        verifyNoInteractions(deliverReminderNotificationPort, saveReminderPort);
    }

    @Test
    void scanAndPublishDueRemindersShouldKeepReminderPendingWhenDeliveryIsSkipped() {
        Reminder dueReminder = pendingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", NOW.minusSeconds(60));
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(loadDueRemindersPort.loadDueReminders(NOW)).thenReturn(List.of(dueReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(java.util.Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(java.util.Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any(ReminderNotificationV1.class)))
                .thenReturn(ReminderNotificationDeliveryResult.skipped("telegram delivery is disabled"));

        int publishedCount = service.scanAndPublishDueReminders(NOW);

        assertEquals(0, publishedCount);
        assertEquals(ReminderStatus.PENDING, dueReminder.getStatus());
        verify(deliverReminderNotificationPort).deliver(any(ReminderNotificationV1.class));
        verifyNoInteractions(saveReminderPort);
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

    private Task task(String taskId, String title) {
        return Task.restore(
                taskId(taskId),
                userId("11111111-1111-1111-1111-111111111111"),
                userId("11111111-1111-1111-1111-111111111111"),
                title,
                "Check prod rollout",
                TaskStatus.OPEN,
                TaskPriority.MEDIUM,
                null,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600)
        );
    }

    private User user(String userId, TelegramChatId telegramChatId) {
        return User.restore(
                userId(userId),
                "alice",
                "Alice DevOps",
                telegramChatId,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600)
        );
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
