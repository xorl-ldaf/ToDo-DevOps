package com.example.todo.application.service;

import com.example.todo.application.command.CreateUserCommand;
import com.example.todo.application.exception.AlreadyExistsException;
import com.example.todo.application.exception.ApplicationValidationException;
import com.example.todo.application.port.out.LoadUserPort;
import com.example.todo.application.port.out.SaveUserPort;
import com.example.todo.domain.shared.TelegramChatId;
import com.example.todo.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveUserPort saveUserPort;

    private CreateUserService service;

    @BeforeEach
    void setUp() {
        service = new CreateUserService(loadUserPort, saveUserPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createUserShouldSaveNewUserWhenUsernameIsAvailable() {
        CreateUserCommand command = new CreateUserCommand(
                "alice",
                "Alice",
                new TelegramChatId(123456789L)
        );
        when(loadUserPort.existsByUsername("alice")).thenReturn(false);
        when(saveUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = service.createUser(command);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(loadUserPort).existsByUsername("alice");
        verify(saveUserPort).save(userCaptor.capture());
        verifyNoMoreInteractions(loadUserPort, saveUserPort);

        User savedUser = userCaptor.getValue();
        assertEquals("alice", createdUser.getUsername());
        assertEquals("Alice", createdUser.getDisplayName());
        assertEquals(command.telegramChatId(), createdUser.getTelegramChatId());
        assertEquals(NOW, createdUser.getCreatedAt());
        assertEquals(NOW, createdUser.getUpdatedAt());

        assertEquals("alice", savedUser.getUsername());
        assertEquals("Alice", savedUser.getDisplayName());
        assertEquals(command.telegramChatId(), savedUser.getTelegramChatId());
        assertEquals(NOW, savedUser.getCreatedAt());
        assertEquals(NOW, savedUser.getUpdatedAt());
    }

    @Test
    void createUserShouldRejectBlankUsername() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.createUser(new CreateUserCommand("   ", "Alice", null))
        );

        assertEquals("username must not be blank", exception.getMessage());
        verifyNoInteractions(loadUserPort, saveUserPort);
    }

    @Test
    void createUserShouldRejectBlankDisplayName() {
        ApplicationValidationException exception = assertThrows(
                ApplicationValidationException.class,
                () -> service.createUser(new CreateUserCommand("alice", "", null))
        );

        assertEquals("displayName must not be blank", exception.getMessage());
        verifyNoInteractions(loadUserPort, saveUserPort);
    }

    @Test
    void createUserShouldRejectDuplicateUsername() {
        when(loadUserPort.existsByUsername("alice")).thenReturn(true);

        AlreadyExistsException exception = assertThrows(
                AlreadyExistsException.class,
                () -> service.createUser(new CreateUserCommand("alice", "Alice", null))
        );

        assertEquals("username already exists: alice", exception.getMessage());
        verify(loadUserPort).existsByUsername("alice");
        verifyNoMoreInteractions(loadUserPort);
        verifyNoInteractions(saveUserPort);
    }
}
