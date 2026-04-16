package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.example.todo.adapter.out.persistence.repository.SpringDataUserRepository;
import com.example.todo.application.port.out.LoadAllUsersPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveUserPort;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;

import java.util.List;
import java.util.Optional;

public class UserPersistenceAdapter implements LoadUserPort, LoadUserDetailsPort, LoadAllUsersPort, SaveUserPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsById(UserId userId) {
        return repository.existsById(userId.value());
    }

    @Override
    public boolean existsByUsername(String username) {
        return repository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<User> loadById(UserId userId) {
        return repository.findById(userId.value())
                .map(UserPersistenceMapper::toDomain);
    }

    @Override
    public List<User> loadAll() {
        return repository.findAll()
                .stream()
                .map(UserPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public User save(User user) {
        return UserPersistenceMapper.toDomain(
                repository.save(UserPersistenceMapper.toJpa(user))
        );
    }
}