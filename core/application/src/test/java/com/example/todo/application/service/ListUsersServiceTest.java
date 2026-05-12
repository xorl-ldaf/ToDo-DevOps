package com.example.todo.application.service;

import com.example.todo.application.port.out.LoadAllUsersPort;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersServiceTest {

    @Mock
    private LoadAllUsersPort loadAllUsersPort;

    private ListUsersService service;

    @BeforeEach
    void setUp() {
        service = new ListUsersService(loadAllUsersPort);
    }

    @Test
    void listUsersShouldDelegateToLoadPort() {
        User firstUser = User.restore(
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "alice",
                "Alice",
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
        User secondUser = User.restore(
                new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                "bob",
                "Bob",
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
        when(loadAllUsersPort.loadAll()).thenReturn(List.of(firstUser, secondUser));

        List<User> result = service.listUsers();

        assertEquals(List.of(firstUser, secondUser), result);
        verify(loadAllUsersPort).loadAll();
        verifyNoMoreInteractions(loadAllUsersPort);
    }
}
