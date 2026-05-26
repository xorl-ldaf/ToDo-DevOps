package com.example.todo.adapter.out.persistence.adapter;

import com.example.todo.adapter.out.persistence.entity.UserJpaEntity;
import com.example.todo.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.example.todo.adapter.out.persistence.exception.PersistenceAdapterFailures;
import com.example.todo.adapter.out.persistence.repository.SpringDataUserRepository;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.LoadAllUsersPort;
import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveUserPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;

public class UserPersistenceAdapter implements LoadUserPort, LoadUserDetailsPort, LoadAllUsersPort, SaveUserPort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsById(UserId userId) {
        return PersistenceAdapterFailures.execute("Check user existence", () -> repository.existsById(userId.value()));
    }

    @Override
    public boolean existsByUsername(String username) {
        return PersistenceAdapterFailures.execute(
                "Check username existence",
                () -> repository.existsByUsernameIgnoreCase(username)
        );
    }

    @Override
    public Optional<User> loadById(UserId userId) {
        return PersistenceAdapterFailures.execute(
                "Load user",
                () -> repository.findById(userId.value())
                        .map(UserPersistenceMapper::toDomain)
        );
    }

    @Override
    public PageResult<User> load(PageQuery pageQuery) {
        return PersistenceAdapterFailures.execute(
                "Load users",
                () -> toPageResult(repository.findAll(toPageable(pageQuery)))
        );
    }

    @Override
    public User save(User user) {
        return PersistenceAdapterFailures.execute(
                "Save user",
                () -> UserPersistenceMapper.toDomain(
                        repository.save(UserPersistenceMapper.toJpa(user))
                )
        );
    }

    private static PageResult<User> toPageResult(Page<UserJpaEntity> page) {
        return new PageResult<>(
                page.getContent().stream().map(UserPersistenceMapper::toDomain).toList(),
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
                toSort(pageQuery.sort(), Sort.by(Sort.Direction.ASC, "username"))
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
                "username", "username",
                "displayName", "displayName",
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
