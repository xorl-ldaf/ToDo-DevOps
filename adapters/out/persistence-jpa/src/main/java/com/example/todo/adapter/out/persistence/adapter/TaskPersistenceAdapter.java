package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.TaskJpaEntity;
import com.example.todo.adapter.out.persistence.mapper.TaskPersistenceMapper;
import com.example.todo.adapter.out.persistence.exception.PersistenceAdapterFailures;
import com.example.todo.adapter.out.persistence.repository.SpringDataTaskRepository;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.LoadAllTasksPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;
import com.example.todo.domain.task.TaskPriority;
import com.example.todo.domain.task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;

public class TaskPersistenceAdapter implements LoadTaskPort, SaveTaskPort, LoadAllTasksPort {

    private final SpringDataTaskRepository repository;

    public TaskPersistenceAdapter(SpringDataTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Task> loadById(TaskId taskId) {
        return PersistenceAdapterFailures.execute(
                "Load task",
                () -> repository.findById(taskId.value())
                        .map(TaskPersistenceMapper::toDomain)
        );
    }

    @Override
    public PageResult<Task> load(PageQuery pageQuery, TaskStatus status, TaskPriority priority) {
        return PersistenceAdapterFailures.execute(
                "Load tasks",
                () -> toPageResult(loadPage(pageQuery, status, priority))
        );
    }

    @Override
    public Task save(Task task) {
        return PersistenceAdapterFailures.execute(
                "Save task",
                () -> TaskPersistenceMapper.toDomain(
                        repository.save(TaskPersistenceMapper.toJpa(task))
                )
        );
    }

    private Page<TaskJpaEntity> loadPage(PageQuery pageQuery, TaskStatus status, TaskPriority priority) {
        Pageable pageable = toPageable(pageQuery);
        if (status != null && priority != null) {
            return repository.findByStatusAndPriority(status, priority, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        if (priority != null) {
            return repository.findByPriority(priority, pageable);
        }
        return repository.findAll(pageable);
    }

    private static PageResult<Task> toPageResult(Page<TaskJpaEntity> page) {
        return new PageResult<>(
                page.getContent().stream().map(TaskPersistenceMapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private static Pageable toPageable(PageQuery pageQuery) {
        return PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                toSort(pageQuery.sort(), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    private static Sort toSort(String sort, Sort defaultSort) {
        if (sort == null) {
            return defaultSort;
        }

        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new ApplicationValidationException("sort must use format field,direction");
        }

        String property = Map.of(
                "id", "id",
                "title", "title",
                "status", "status",
                "priority", "priority",
                "dueAt", "dueAt",
                "createdAt", "createdAt",
                "updatedAt", "updatedAt"
        ).get(parts[0].trim());
        if (property == null) {
            throw new ApplicationValidationException("unsupported sort field: " + parts[0].trim());
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2 && !parts[1].isBlank()) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new ApplicationValidationException("unsupported sort direction: " + parts[1].trim()));
        }

        return Sort.by(direction, property);
    }
}
