package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.mapper.TaskPersistenceMapper;
import com.example.todo.adapter.out.persistence.repository.SpringDataTaskRepository;
import com.example.todo.application.port.out.LoadAllTasksPort;
import com.example.todo.application.port.out.LoadTaskPort;
import com.example.todo.application.port.out.SaveTaskPort;
import com.example.todo.domain.task.Task;
import com.example.todo.domain.task.TaskId;

import java.util.List;
import java.util.Optional;

public class TaskPersistenceAdapter implements LoadTaskPort, SaveTaskPort, LoadAllTasksPort {

    private final SpringDataTaskRepository repository;

    public TaskPersistenceAdapter(SpringDataTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Task> loadById(TaskId taskId) {
        return repository.findById(taskId.value())
                .map(TaskPersistenceMapper::toDomain);
    }

    @Override
    public List<Task> loadAll() {
        return repository.findAll()
                .stream()
                .map(TaskPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Task save(Task task) {
        return TaskPersistenceMapper.toDomain(
                repository.save(TaskPersistenceMapper.toJpa(task))
        );
    }
}