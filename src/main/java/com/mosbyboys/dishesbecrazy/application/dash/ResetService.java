package com.mosbyboys.dishesbecrazy.application.dash;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoDataRestorer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResetService {
    private final REGFacade registration;
    private final DemoDataRestorer database;
    private final OrderQueueLoader orderQueues;
    private final AtomicBoolean restoring = new AtomicBoolean();

    public void restoreDatabaseAndOrderQueues(UUID operatorId) throws IOException {
        var caller = registration.getCurrentUser(operatorId);
        if (caller.getRole() != Role.OPERATOR) {
            throw new PermissionDeniedException("Only an operator can restore the demo.");
        }
        Assert.state(!TransactionSynchronizationManager.isActualTransactionActive(),
                "Demo restoration must start outside an existing transaction.");
        Assert.state(restoring.compareAndSet(false, true), "Demo restoration is already in progress.");
        // Check the persisted role and reject overlapping restores before changing either database or queues.

        try {
            database.restore();
            orderQueues.rebuild();
        } finally {
            restoring.set(false);
        }
        // Database restoration commits first. A failed queue rebuild propagates and a retry repeats both steps.
    }
    // Internal preparation only: callers must stop demo commands; DLV coordination is required before exposing Reset.
}
