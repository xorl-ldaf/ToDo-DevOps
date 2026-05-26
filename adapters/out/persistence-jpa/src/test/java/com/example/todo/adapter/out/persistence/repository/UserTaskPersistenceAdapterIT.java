package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTaskPersistenceAdapterIT extends AbstractReminderPersistenceRepositoryIT {

    @Test
    void saveAndLoadUserShouldPreserveFieldsAndSupportCaseInsensitiveUsernameExistence() {
        UserId userId = userId("11111111-1111-1111-1111-111111111111");
        User user = new User(
                userId,
                "Alice.Persistence",
                "Alice Persistence",
                new TelegramChatId(123456789L),
                NOW.minusSeconds(60),
                NOW
        );

        User saved = userAdapter.save(user);

        assertThat(saved).isEqualTo(user);
        assertThat(userAdapter.loadById(userId)).hasValue(user);
        assertThat(userAdapter.existsById(userId)).isTrue();
        assertThat(userAdapter.existsByUsername("alice.persistence")).isTrue();
        assertThat(userAdapter.existsByUsername("ALICE.PERSISTENCE")).isTrue();
    }

    @Test
    void loadUsersShouldReturnStablePagedObject() {
        User alice = new User(
                userId("11111111-1111-1111-1111-111111111111"),
                "alice",
                "Alice",
                null,
                NOW.minusSeconds(120),
                NOW.minusSeconds(120)
        );
        User bob = new User(
                userId("22222222-2222-2222-2222-222222222222"),
                "bob",
                "Bob",
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)
        );
        userAdapter.save(bob);
        userAdapter.save(alice);

        PageResult<User> page = userAdapter.load(new PageQuery(0, 1, "username,asc"));

        assertThat(page.items()).containsExactly(alice);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void saveAndLoadTaskShouldPreserveFieldsAndSupportPagingFilters() {
        User author = new User(
                userId("11111111-1111-1111-1111-111111111111"),
                "task.author",
                "Task Author",
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
        User assignee = new User(
                userId("22222222-2222-2222-2222-222222222222"),
                "task.assignee",
                "Task Assignee",
                null,
                NOW.minusSeconds(300),
                NOW.minusSeconds(300)
        );
        userAdapter.save(author);
        userAdapter.save(assignee);

        Task matchingTask = task(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                author.getId(),
                assignee.getId(),
                "Critical persisted task",
                TaskStatus.OPEN,
                TaskPriority.CRITICAL,
                NOW.plusSeconds(3600)
        );
        Task otherTask = task(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                author.getId(),
                author.getId(),
                "Medium persisted task",
                TaskStatus.DONE,
                TaskPriority.MEDIUM,
                null
        );

        Task saved = taskAdapter.save(matchingTask);
        taskAdapter.save(otherTask);

        assertThat(saved).isEqualTo(matchingTask);
        assertThat(taskAdapter.loadById(matchingTask.getId())).hasValue(matchingTask);

        PageResult<Task> filtered = taskAdapter.load(
                new PageQuery(0, 10, "createdAt,desc"),
                TaskStatus.OPEN,
                TaskPriority.CRITICAL
        );
        assertThat(filtered.items()).containsExactly(matchingTask);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.totalPages()).isEqualTo(1);
    }

    private Task task(
            String taskId,
            UserId authorId,
            UserId assigneeId,
            String title,
            TaskStatus status,
            TaskPriority priority,
            Instant dueAt
    ) {
        return new Task(
                new TaskId(UUID.fromString(taskId)),
                authorId,
                assigneeId,
                title,
                "Persisted task description",
                status,
                priority,
                dueAt,
                NOW.minusSeconds(120),
                NOW.minusSeconds(60)
        );
    }

    private UserId userId(String value) {
        return new UserId(UUID.fromString(value));
    }
}
