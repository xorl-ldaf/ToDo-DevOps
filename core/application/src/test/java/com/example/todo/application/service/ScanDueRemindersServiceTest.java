package com.example.todo.application.service;

import com.example.todo.application.port.in.ReminderProcessingReport;
import com.example.todo.application.port.out.ClaimDueRemindersPort;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.FinalizeReminderDeliveryPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.example.todo.domain.reminder.Reminder;
import com.example.todo.domain.reminder.ReminderId;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanDueRemindersServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final String PROCESSOR_ID = "worker-a";

    @Mock
    private ClaimDueRemindersPort claimDueRemindersPort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private LoadUserDetailsPort loadUserDetailsPort;

    @Mock
    private DeliverReminderNotificationPort deliverReminderNotificationPort;

    @Mock
    private FinalizeReminderDeliveryPort finalizeReminderDeliveryPort;

    private ScanDueRemindersService service;

    @BeforeEach
    void setUp() {
        service = new ScanDueRemindersService(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort,
                PROCESSOR_ID,
                25,
                3,
                Duration.ofMinutes(5),
                Duration.ofSeconds(30)
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldClaimDeliverAndFinalizeDeliveredReminder() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any())).thenReturn(ReminderNotificationDeliveryResult.delivered());
        when(finalizeReminderDeliveryPort.markDelivered(claimedReminder.getId(), PROCESSOR_ID, NOW)).thenReturn(true);

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        ArgumentCaptor<com.example.todo.application.notification.ReminderNotificationV1> notificationCaptor =
                ArgumentCaptor.forClass(com.example.todo.application.notification.ReminderNotificationV1.class);
        InOrder inOrder = inOrder(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort
        );
        inOrder.verify(claimDueRemindersPort).claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25);
        inOrder.verify(loadTaskPort).loadById(claimedReminder.getTaskId());
        inOrder.verify(loadUserDetailsPort).loadById(task.getAssigneeId());
        inOrder.verify(deliverReminderNotificationPort).deliver(notificationCaptor.capture());
        inOrder.verify(finalizeReminderDeliveryPort).markDelivered(claimedReminder.getId(), PROCESSOR_ID, NOW);
        verifyNoMoreInteractions(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort
        );

        assertEquals(new ReminderProcessingReport(1, 1, 0, 0, 0), report);
        assertEquals(task.getId().value(), notificationCaptor.getValue().taskId());
        assertEquals(recipient.getId().value(), notificationCaptor.getValue().recipientUserId());
    }

    @Test
    void scanAndPublishDueRemindersShouldValidateNow() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.scanAndPublishDueReminders(null)
        );

        assertEquals("now must not be null", exception.getMessage());
        verifyNoInteractions(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldReturnEmptyReportWhenNothingIsClaimed() {
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of());

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        assertEquals(ReminderProcessingReport.empty(), report);
        verify(claimDueRemindersPort).claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25);
        verifyNoMoreInteractions(claimDueRemindersPort);
        verifyNoInteractions(loadTaskPort, loadUserDetailsPort, deliverReminderNotificationPort, finalizeReminderDeliveryPort);
    }

    @Test
    void scanAndPublishDueRemindersShouldFailReminderWhenRecipientHasNoTelegramChat() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", null);
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(finalizeReminderDeliveryPort.markFailed(
                claimedReminder.getId(),
                PROCESSOR_ID,
                NOW,
                "recipient has no telegram chat id"
        )).thenReturn(true);

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        verifyNoInteractions(deliverReminderNotificationPort);
        verify(finalizeReminderDeliveryPort).markFailed(
                claimedReminder.getId(),
                PROCESSOR_ID,
                NOW,
                "recipient has no telegram chat id"
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldRescheduleReminderWhenDeliveryFailsTransiently() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any()))
                .thenReturn(ReminderNotificationDeliveryResult.retryableFailure("telegram timeout"));
        when(finalizeReminderDeliveryPort.reschedule(
                eq(claimedReminder.getId()),
                eq(PROCESSOR_ID),
                eq(NOW),
                eq(NOW.plus(Duration.ofMinutes(5))),
                eq("telegram timeout")
        )).thenReturn(true);

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 1, 0, 0), report);
        verify(finalizeReminderDeliveryPort).reschedule(
                claimedReminder.getId(),
                PROCESSOR_ID,
                NOW,
                NOW.plus(Duration.ofMinutes(5)),
                "telegram timeout"
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldFailReminderWhenRetryBudgetIsExhausted() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any()))
                .thenReturn(ReminderNotificationDeliveryResult.retryableFailure("telegram timeout"));
        when(finalizeReminderDeliveryPort.markFailed(
                claimedReminder.getId(),
                PROCESSOR_ID,
                NOW,
                "telegram timeout"
        )).thenReturn(true);

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        verify(finalizeReminderDeliveryPort).markFailed(
                claimedReminder.getId(),
                PROCESSOR_ID,
                NOW,
                "telegram timeout"
        );
    }

    @Test
    void scanAndPublishDueRemindersShouldReportConflictWhenFinalizeReturnsFalse() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(NOW, PROCESSOR_ID, Duration.ofSeconds(30), 25))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any())).thenReturn(ReminderNotificationDeliveryResult.delivered());
        when(finalizeReminderDeliveryPort.markDelivered(claimedReminder.getId(), PROCESSOR_ID, NOW)).thenReturn(false);

        ReminderProcessingReport report = service.scanAndPublishDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 0, 1), report);
    }

    private Reminder processingReminder(String reminderId, int deliveryAttempts) {
        return Reminder.restore(
                new ReminderId(UUID.fromString(reminderId)),
                taskId("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                NOW.minusSeconds(60),
                ReminderStatus.PROCESSING,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                NOW.minusSeconds(60),
                NOW.minusSeconds(1),
                PROCESSOR_ID,
                null,
                deliveryAttempts,
                null
        );
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
