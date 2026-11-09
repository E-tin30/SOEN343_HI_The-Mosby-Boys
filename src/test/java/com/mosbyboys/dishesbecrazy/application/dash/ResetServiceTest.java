package com.mosbyboys.dishesbecrazy.application.dash;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoDataRestorer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResetServiceTest {
    private final UUID operatorId = UUID.randomUUID();
    private REGFacade registration;
    private DemoDataRestorer database;
    private OrderQueueLoader queues;
    private ResetService reset;

    @BeforeEach
    void prepareService() {
        registration = mock(REGFacade.class);
        database = mock(DemoDataRestorer.class);
        queues = mock(OrderQueueLoader.class);
        reset = new ResetService(registration, database, queues);
        var operator = mock(User.class);
        when(operator.getRole()).thenReturn(Role.OPERATOR);
        when(registration.getCurrentUser(operatorId)).thenReturn(operator);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotRestore(Role role) {
        var caller = mock(User.class);
        when(caller.getRole()).thenReturn(role);
        when(registration.getCurrentUser(operatorId)).thenReturn(caller);

        assertThrows(PermissionDeniedException.class, () -> reset.restoreDatabaseAndOrderQueues(operatorId));

        verifyNoInteractions(database, queues);
    }

    @Test
    void aDatabaseFailureSkipsQueueChangesAndAllowsRetry() throws Exception {
        var failure = new IOException("Fixture unavailable");
        doThrow(failure).doNothing().when(database).restore();

        assertSame(failure, assertThrows(IOException.class, () -> reset.restoreDatabaseAndOrderQueues(operatorId)));
        verifyNoInteractions(queues);

        reset.restoreDatabaseAndOrderQueues(operatorId);

        verify(database, times(2)).restore();
        verify(queues).rebuild();
    }

    @Test
    void aQueueFailureIsReportedAndRetryRepeatsBothSteps() throws Exception {
        var failure = new IllegalStateException("Waiting orders unavailable");
        doThrow(failure).doNothing().when(queues).rebuild();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> reset.restoreDatabaseAndOrderQueues(operatorId)));

        reset.restoreDatabaseAndOrderQueues(operatorId);

        verify(database, times(2)).restore();
        verify(queues, times(2)).rebuild();
    }

    @Test
    void overlappingRestoresAreRejectedWithoutStartingAnotherDatabaseWrite() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(database).restore();

        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(() -> {
                reset.restoreDatabaseAndOrderQueues(operatorId);
                return null;
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var error = assertThrows(IllegalStateException.class, () -> reset.restoreDatabaseAndOrderQueues(operatorId));
                assertEquals("Demo restoration is already in progress.", error.getMessage());
                verify(database).restore();
                verifyNoInteractions(queues);
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }

        reset.restoreDatabaseAndOrderQueues(operatorId);
        verify(database, times(2)).restore();
        verify(queues, times(2)).rebuild();
    }
    // Latches hold one real invocation open, so the overlap check does not depend on sleeps or thread timing.
}
