package com.example.todo.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    private UUID id;
    private String username;
    private String displayName;
    private Long telegramChatId;
    private Instant createdAt;
    private Instant updatedAt;

    public UserJpaEntity() {
    }
}