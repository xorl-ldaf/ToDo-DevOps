package com.example.todo.adapter.out.persistence.repository;

import com.example.todo.adapter.out.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {
    boolean existsByUsernameIgnoreCase(String username);
}