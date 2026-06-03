package com.example.todo.application.service;

import com.example.todo.application.exception.ApplicationValidationException;
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
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void processDueRemindersShouldClaimDeliverAndFinalizeDeliveredReminder() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any())).thenReturn(ReminderNotificationDeliveryResult.delivered());
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        ArgumentCaptor<com.example.todo.application.notification.ReminderNotificationV1> notificationCaptor =
                ArgumentCaptor.forClass(com.example.todo.application.notification.ReminderNotificationV1.class);
        ArgumentCaptor<Reminder> finalizedReminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        InOrder inOrder = inOrder(
                claimDueRemindersPort,
                loadTaskPort,
                loadUserDetailsPort,
                deliverReminderNotificationPort,
                finalizeReminderDeliveryPort
        );
        inOrder.verify(claimDueRemindersPort).claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any());
        inOrder.verify(loadTaskPort).loadById(claimedReminder.getTaskId());
        inOrder.verify(loadUserDetailsPort).loadById(task.getAssigneeId());
        inOrder.verify(deliverReminderNotificationPort).deliver(notificationCaptor.capture());
        inOrder.verify(finalizeReminderDeliveryPort).finalizeDelivery(finalizedReminderCaptor.capture(), eq(PROCESSOR_ID));
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
        Reminder finalizedReminder = finalizedReminderCaptor.getValue();
        assertEquals(ReminderStatus.DELIVERED, finalizedReminder.getStatus());
        assertEquals(NOW, finalizedReminder.getDeliveredAt());
        assertEquals(1, finalizedReminder.getDeliveryAttempts());
        assertNull(finalizedReminder.getProcessingOwner());
        assertNull(finalizedReminder.getProcessingStartedAt());
    }

    @Test
    void processDueRemindersShouldValidateNow() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.processDueReminders(null)
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
    void processDueRemindersShouldReturnEmptyReportWhenNothingIsClaimed() {
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of());

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(ReminderProcessingReport.empty(), report);
        verify(claimDueRemindersPort).claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any());
        verifyNoMoreInteractions(claimDueRemindersPort);
        verifyNoInteractions(loadTaskPort, loadUserDetailsPort, deliverReminderNotificationPort, finalizeReminderDeliveryPort);
    }

    @Test
    void processDueRemindersShouldProvideApplicationClaimTransition() {
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of());

        service.processDueReminders(NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UnaryOperator<Reminder>> claimTransitionCaptor =
                (ArgumentCaptor<UnaryOperator<Reminder>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(UnaryOperator.class);
        verify(claimDueRemindersPort).claimDueReminders(
                eq(NOW),
                eq(Duration.ofSeconds(30)),
                eq(25),
                claimTransitionCaptor.capture()
        );

        Reminder claimedReminder = claimTransitionCaptor.getValue()
                .apply(scheduledReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertEquals(ReminderStatus.PROCESSING, claimedReminder.getStatus());
        assertEquals(PROCESSOR_ID, claimedReminder.getProcessingOwner());
        assertEquals(NOW, claimedReminder.getProcessingStartedAt());
        assertEquals(NOW, claimedReminder.getUpdatedAt());
        assertNull(claimedReminder.getLastFailureReason());
    }

    @Test
    void processDueRemindersShouldFailReminderWhenTaskIsMissing() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(claimedReminder.getTaskId())).thenReturn(Optional.empty());
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        verifyNoInteractions(loadUserDetailsPort, deliverReminderNotificationPort);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.FAILED, finalizedReminder.getStatus());
        assertEquals("task no longer exists", finalizedReminder.getLastFailureReason());
    }

    @Test
    void processDueRemindersShouldFailReminderWhenRecipientIsMissing() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.empty());
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        verifyNoInteractions(deliverReminderNotificationPort);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.FAILED, finalizedReminder.getStatus());
        assertEquals("assignee no longer exists", finalizedReminder.getLastFailureReason());
    }

    @Test
    void processDueRemindersShouldFailReminderWhenRecipientHasNoTelegramChat() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", null);
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        verifyNoInteractions(deliverReminderNotificationPort);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.FAILED, finalizedReminder.getStatus());
        assertEquals("recipient has no telegram chat id", finalizedReminder.getLastFailureReason());
    }

    @Test
    void processDueRemindersShouldRescheduleReminderWhenDeliveryFailsTransiently() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any()))
                .thenReturn(ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"));
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 1, 0, 0), report);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.SCHEDULED, finalizedReminder.getStatus());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), finalizedReminder.getNextAttemptAt());
        assertEquals("transient notification failure", finalizedReminder.getLastFailureReason());
        assertEquals(1, finalizedReminder.getDeliveryAttempts());
        assertNull(finalizedReminder.getProcessingOwner());
        assertNull(finalizedReminder.getProcessingStartedAt());
    }

    @Test
    void processDueRemindersShouldFailReminderImmediatelyWhenDeliveryFailureIsNonRetryable() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any()))
                .thenReturn(ReminderNotificationDeliveryResult.permanentFailure("recipient cannot receive notifications"));
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.FAILED, finalizedReminder.getStatus());
        assertEquals("recipient cannot receive notifications", finalizedReminder.getLastFailureReason());
        verifyNoMoreInteractions(finalizeReminderDeliveryPort);
    }

    @Test
    void processDueRemindersShouldFailReminderWhenRetryBudgetIsExhausted() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any()))
                .thenReturn(ReminderNotificationDeliveryResult.retryableFailure("transient notification failure"));
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(true);

        ReminderProcessingReport report = service.processDueReminders(NOW);

        assertEquals(new ReminderProcessingReport(1, 0, 0, 1, 0), report);
        Reminder finalizedReminder = captureFinalizedReminder();
        assertEquals(ReminderStatus.FAILED, finalizedReminder.getStatus());
        assertEquals("transient notification failure", finalizedReminder.getLastFailureReason());
        assertEquals(3, finalizedReminder.getDeliveryAttempts());
    }

    @Test
    void processDueRemindersShouldReportConcurrencyConflictWhenFinalizeOwnerDoesNotMatch() {
        Reminder claimedReminder = processingReminder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0);
        Task task = task("ffffffff-ffff-ffff-ffff-ffffffffffff", "Review rollout");
        User recipient = user("11111111-1111-1111-1111-111111111111", new TelegramChatId(123456789L));
        when(claimDueRemindersPort.claimDueReminders(eq(NOW), eq(Duration.ofSeconds(30)), eq(25), any()))
                .thenReturn(List.of(claimedReminder));
        when(loadTaskPort.loadById(task.getId())).thenReturn(Optional.of(task));
        when(loadUserDetailsPort.loadById(task.getAssigneeId())).thenReturn(Optional.of(recipient));
        when(deliverReminderNotificationPort.deliver(any())).thenReturn(ReminderNotificationDeliveryResult.delivered());
        when(finalizeReminderDeliveryPort.finalizeDelivery(any(), eq(PROCESSOR_ID))).thenReturn(false);

        ReminderProcessingReport report = service.processDueReminders(NOW);

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

    private Reminder scheduledReminder(String reminderId) {
        return Reminder.restore(
                new ReminderId(UUID.fromString(reminderId)),
                taskId("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                NOW.minusSeconds(60),
                ReminderStatus.SCHEDULED,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                NOW.minusSeconds(60),
                null,
                null,
                null,
                0,
                "previous timeout"
        );
    }

    private Reminder captureFinalizedReminder() {
        ArgumentCaptor<Reminder> finalizedReminderCaptor = ArgumentCaptor.forClass(Reminder.class);
        verify(finalizeReminderDeliveryPort).finalizeDelivery(finalizedReminderCaptor.capture(), eq(PROCESSOR_ID));
        return finalizedReminderCaptor.getValue();
    }

    private TaskId taskId(String value) {
        return new TaskId(UUID.fromString(value));
    }

    private Task task(String taskId, String title) {
        return new Task(
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
        return new User(
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
