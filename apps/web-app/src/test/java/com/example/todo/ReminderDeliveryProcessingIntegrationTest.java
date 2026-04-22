package com.example.todo;

import com.example.todo.application.notification.ReminderNotificationV1;
import com.example.todo.application.port.in.ReminderProcessingReport;
import com.example.todo.application.port.in.ScanDueRemindersUseCase;
import com.example.todo.application.port.out.DeliverReminderNotificationPort;
import com.example.todo.application.port.out.ReminderNotificationDeliveryResult;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = {WebApplication.class, ReminderDeliveryProcessingIntegrationTest.TestConfig.class})
@Testcontainers
@ActiveProfiles("test")
class ReminderDeliveryProcessingIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-04-21T10:00:00Z");
    private static final Instant REMIND_AT = Instant.parse("2026-04-21T10:30:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("todo.reminder-delivery.batch-size", () -> "1");
        registry.add("todo.reminder-delivery.max-attempts", () -> "3");
        registry.add("todo.reminder-delivery.retry-backoff", () -> "5s");
        registry.add("todo.reminder-delivery.processing-timeout", () -> "30s");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock testClock;

    @Autowired
    private ScanDueRemindersUseCase scanDueRemindersUseCase;

    @Autowired
    private InspectableNotificationSender notificationSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE reminder_scheduled_event_receipts, reminder_scheduled_event_outbox, reminders, tasks, users CASCADE");
        testClock.setInstant(INITIAL_TIME);
        notificationSender.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void deliveryShouldHappenOutsideDatabaseTransaction() throws Exception {
        String userId = createUser("txn.user", "Txn User", 123456789L);
        String taskId = createTask("Check transaction boundary", userId);
        createReminder(taskId, REMIND_AT.toString());

        testClock.setInstant(REMIND_AT);
        ReminderProcessingReport report = scanDueRemindersUseCase.scanAndPublishDueReminders(REMIND_AT);

        assertEquals(1, report.deliveredCount());
        assertFalse(notificationSender.wasTransactionActiveDuringCall());
    }

    @Test
    void concurrentScansShouldClaimAndDeliverDueRemindersWithoutDuplicates() throws Exception {
        String userId = createUser("concurrent.user", "Concurrent User", 123456789L);
        String taskId = createTask("Concurrent reminder one", userId);
        String firstReminderId = createReminder(taskId, REMIND_AT.toString());
        String secondReminderId = createReminder(taskId, REMIND_AT.plusSeconds(1).toString());

        testClock.setInstant(REMIND_AT.plusSeconds(1));
        notificationSender.enableBlocking(2);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ReminderProcessingReport> first = executorService.submit(
                    () -> scanDueRemindersUseCase.scanAndPublishDueReminders(REMIND_AT.plusSeconds(1))
            );
            Future<ReminderProcessingReport> second = executorService.submit(
                    () -> scanDueRemindersUseCase.scanAndPublishDueReminders(REMIND_AT.plusSeconds(1))
            );

            assertTrue(notificationSender.awaitStarted(5, TimeUnit.SECONDS));
            notificationSender.releaseBlockedCalls();

            ReminderProcessingReport firstReport = first.get(5, TimeUnit.SECONDS);
            ReminderProcessingReport secondReport = second.get(5, TimeUnit.SECONDS);

            assertEquals(2, firstReport.deliveredCount() + secondReport.deliveredCount());
            assertEquals(Set.of(UUID.fromString(firstReminderId), UUID.fromString(secondReminderId)),
                    notificationSender.deliveredReminderIds());
            Integer deliveredCount = jdbcTemplate.queryForObject(
                    "select count(*) from reminders where status = 'DELIVERED'",
                    Integer.class
            );
            assertEquals(2, deliveredCount);
        }
    }

    private String createUser(String username, String displayName, Long telegramChatId) throws Exception {
        String requestBody = """
                {
                  "username": "%s",
                  "displayName": "%s",
                  "telegramChatId": %d
                }
                """.formatted(username, displayName, telegramChatId);

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTask(String title, String authorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Background processing integration test",
                                  "authorId": "%s"
                                }
                                """.formatted(title, authorId)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createReminder(String taskId, String remindAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/{taskId}/reminders", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remindAt": "%s"
                                }
                                """.formatted(remindAt)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(INITIAL_TIME, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        InspectableNotificationSender inspectableNotificationSender() {
            return new InspectableNotificationSender();
        }
    }

    static final class InspectableNotificationSender implements DeliverReminderNotificationPort {
        private final Set<UUID> deliveredReminderIds = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean transactionActiveDuringCall = new AtomicBoolean(false);
        private volatile CountDownLatch startedLatch = new CountDownLatch(0);
        private volatile CountDownLatch releaseLatch = new CountDownLatch(0);

        @Override
        public ReminderNotificationDeliveryResult deliver(ReminderNotificationV1 notification) {
            transactionActiveDuringCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            deliveredReminderIds.add(notification.reminderId());
            startedLatch.countDown();
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ReminderNotificationDeliveryResult.retryableFailure("test sender interrupted");
            }
            return ReminderNotificationDeliveryResult.delivered();
        }

        void reset() {
            deliveredReminderIds.clear();
            transactionActiveDuringCall.set(false);
            startedLatch = new CountDownLatch(0);
            releaseLatch = new CountDownLatch(0);
        }

        void enableBlocking(int expectedCalls) {
            startedLatch = new CountDownLatch(expectedCalls);
            releaseLatch = new CountDownLatch(1);
        }

        boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return startedLatch.await(timeout, unit);
        }

        void releaseBlockedCalls() {
            releaseLatch.countDown();
        }

        boolean wasTransactionActiveDuringCall() {
            return transactionActiveDuringCall.get();
        }

        Set<UUID> deliveredReminderIds() {
            return Set.copyOf(deliveredReminderIds);
        }
    }

    static final class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone;

        private MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        private void setInstant(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
