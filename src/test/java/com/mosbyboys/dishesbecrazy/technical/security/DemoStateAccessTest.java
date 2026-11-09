package com.mosbyboys.dishesbecrazy.technical.security;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemoStateAccessTest {
    private final DemoStateAccess access = new DemoStateAccess();

    @Test
    void ordinaryRequestsCanRunTogether() throws Exception {
        try (var first = access.beginRequest(); var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> access.beginRequest().close()).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void restorationWaitsForExistingRequestsAndRefusesNewOnes() throws Exception {
        var existing = access.beginRequest();
        var attempting = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reset = worker.submit(() -> {
                attempting.countDown();
                try (var restoration = access.beginRestoration()) {
                    restoration.complete();
                }
            });
            try {
                assertTrue(attempting.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reset.get(150, TimeUnit.MILLISECONDS));
                assertThrows(IllegalStateException.class, access::beginRequest);
            } finally {
                existing.close();
            }
            reset.get(5, TimeUnit.SECONDS);
        }
        assertDoesNotThrow(() -> access.beginRequest().close());
    }
    // An active request prevents Reset from entering; the pending Reset prevents additional requests from joining it.

    @Test
    void requestsCannotEnterWhileRestorationOwnsExclusiveAccess() {
        try (var restoration = access.beginRestoration()) {
            assertThrows(IllegalStateException.class, access::beginRequest);
            assertThrows(IllegalStateException.class, access::beginRestoration);
            restoration.complete();
        }
        assertDoesNotThrow(() -> access.beginRequest().close());
    }

    @Test
    void failedRuntimeRecoveryRemainsBlockedUntilASuccessfulRetry() {
        try (var restoration = access.beginRestoration()) {
            restoration.databaseRestored();
        }
        assertThrows(IllegalStateException.class, access::beginRequest);
        try (var retry = access.beginRestoration()) { }
        assertThrows(IllegalStateException.class, access::beginRequest);
        try (var retry = access.beginRestoration()) {
            retry.databaseRestored();
            retry.complete();
        }
        assertDoesNotThrow(() -> access.beginRequest().close());
    }
    // A later database failure must not clear the recovery requirement left by an earlier committed restoration.

    @Test
    void normalRequestsCannotUpgradeTheirOwnAccessAndDeadlock() {
        try (var request = access.beginRequest()) {
            assertThrows(IllegalStateException.class, access::beginRestoration);
        }
        try (var restoration = access.beginRestoration()) {
            restoration.complete();
        }
    }
}
