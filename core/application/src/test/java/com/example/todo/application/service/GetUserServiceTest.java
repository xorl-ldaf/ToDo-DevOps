package com.example.todo.application.service;

import com.example.todo.application.port.out.LoadUserDetailsPort;
import com.example.todo.domain.user.User;
import com.example.todo.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserServiceTest {

    @Mock
    private LoadUserDetailsPort loadUserDetailsPort;

    private GetUserService service;

    @BeforeEach
    void setUp() {
        service = new GetUserService(loadUserDetailsPort);
    }

    @Test
    void getUserShouldDelegateToLoadPort() {
        UserId userId = new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        User user = User.restore(userId, "alice", "Alice", null, Instant.parse("2026-04-20T10:00:00Z"), Instant.parse("2026-04-20T10:00:00Z"));
        when(loadUserDetailsPort.loadById(userId)).thenReturn(Optional.of(user));

        Optional<User> result = service.getUser(userId);

        assertEquals(Optional.of(user), result);
        assertSame(user, result.orElseThrow());
        verify(loadUserDetailsPort).loadById(userId);
        verifyNoMoreInteractions(loadUserDetailsPort);
    }
}
