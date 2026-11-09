package com.mosbyboys.dishesbecrazy.technical.security;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class DemoStateAccess {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean restoring = new AtomicBoolean();
    private final long resetWaitMillis;
    private boolean recoveryRequired;

    public DemoStateAccess() {
        this(5_000);
    }

    DemoStateAccess(long resetWaitMillis) {
        Assert.isTrue(resetWaitMillis > 0, "Reset wait must be positive.");
        this.resetWaitMillis = resetWaitMillis;
    }

    public RequestAccess beginRequest() {
        Assert.state(!restoring.get(), "Demo restoration is in progress. Retry shortly.");
        lock.readLock().lock();
        boolean pending = restoring.get();
        if (pending || recoveryRequired) {
            lock.readLock().unlock();
            throw new IllegalStateException(pending
                    ? "Demo restoration is in progress. Retry shortly."
                    : "Demo restoration is incomplete. Retry Reset before continuing.");
        }
        return lock.readLock()::unlock;
    }
    // Requests can run together; recheck after acquiring access so a newly pending Reset cannot be bypassed.

    public Restoration beginRestoration() {
        Assert.state(lock.getReadHoldCount() == 0, "Reset cannot start inside a normal request's shared access.");
        Assert.state(restoring.compareAndSet(false, true), "Demo restoration is already in progress.");
        boolean acquired = false;
        try {
            acquired = lock.writeLock().tryLock(resetWaitMillis, TimeUnit.MILLISECONDS);
            Assert.state(acquired, "Reset timed out waiting for active requests. Retry Reset shortly.");
            return new Restoration();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reset was interrupted while waiting for active requests. Retry Reset.", interrupted);
        } finally {
            if (!acquired) {
                restoring.set(false);
            }
        }
    }
    // Refuse new requests while waiting, but release the pending flag if exclusive access times out or is interrupted.

    public interface RequestAccess extends AutoCloseable {
        @Override
        void close();
    }
    // Scoped access keeps the request lock until controller work, transaction commits and synchronous callbacks finish.

    public final class Restoration implements AutoCloseable {
        private Restoration() { }

        public void databaseRestored() {
            recoveryRequired = true;
        }
        // Once persistence commits, normal requests must stay blocked until runtime rebuilding also succeeds.

        public void complete() {
            recoveryRequired = false;
        }

        @Override
        public void close() {
            lock.writeLock().unlock();
            restoring.set(false);
        }
        // A failed attempt releases exclusive access for an operator retry without clearing an outstanding recovery flag.
    }
}
