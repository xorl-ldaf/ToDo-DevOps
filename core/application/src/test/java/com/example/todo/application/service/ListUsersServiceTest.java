package com.example.todo.application.service;

import com.example.todo.application.port.out.LoadAllUsersPort;
import com.example.todo.application.query.PageQuery;
import com.example.todo.application.query.PageResult;
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
        PageQuery pageQuery = new PageQuery(0, 20, null);
        User firstUser = new User(
                new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "alice",
                "Alice",
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
        User secondUser = new User(
                new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                "bob",
                "Bob",
                null,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-20T10:00:00Z")
        );
        PageResult<User> page = new PageResult<>(List.of(firstUser, secondUser), 0, 20, 2, 1);
        when(loadAllUsersPort.load(pageQuery)).thenReturn(page);

        PageResult<User> result = service.listUsers(pageQuery);

        assertEquals(page, result);
        verify(loadAllUsersPort).load(pageQuery);
        verifyNoMoreInteractions(loadAllUsersPort);
    }
}
