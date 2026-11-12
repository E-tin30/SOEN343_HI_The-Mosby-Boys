package com.mosbyboys.dishesbecrazy.application.dash;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.application.dlv.DispatchQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoDataRestorer;
import com.mosbyboys.dishesbecrazy.technical.security.DemoStateAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResetService {
    private final REGFacade registration;
    private final DemoDataRestorer database;
    private final OrderQueueLoader orderQueues;
    private final DispatchQueueLoader dispatchQueue;
    private final DemoStateAccess access;

    public void restoreDatabaseAndOrderQueues(UUID operatorId) throws IOException {
        var caller = registration.getCurrentUser(operatorId);
        if (caller.getRole() != Role.OPERATOR) {
            throw new PermissionDeniedException("Only an operator can restore the demo.");
        }
        Assert.state(!TransactionSynchronizationManager.isActualTransactionActive(),
                "Demo restoration must start outside an existing transaction.");
        // Check the persisted role and transaction boundary before requesting exclusive access.

        try (var restoration = access.beginRestoration()) {
            database.restore();
            restoration.databaseRestored();
            orderQueues.rebuild();
            dispatchQueue.rebuild();
            restoration.complete();
        }
        // Hold exclusive access through commit and rebuilding; a runtime failure keeps normal requests blocked for retry.
    }
    // Restores the database baseline, the ORD queues and the global ready queue that dispatch reads.
}
