package com.cobbletracker.common.tracker;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandHookGuardTest {
    @Test
    void queuedPlacementRestoresCapturedHookOriginBeforeEntityDetection() {
        CommandHookGuard guard = new CommandHookGuard();
        var commands = new ArrayDeque<Runnable>();
        var detections = new ArrayDeque<CommandHookGuard.Origin>();
        guard.run(CommandHookGuard.Origin.NORMAL, () -> {
            var captured = guard.captureOrigin();
            commands.add(() -> guard.withOrigin(captured, () -> {
                detections.add(guard.captureOrigin());
                return true;
            }));
        });
        assertEquals(CommandHookGuard.Origin.NORMAL, guard.captureOrigin());
        commands.removeFirst().run();
        assertEquals(CommandHookGuard.Origin.COMMAND_HOOK, detections.peekFirst());
        assertFalse(guard.run(detections.removeFirst(), () -> fail("Queued hook started another chain")));
        assertEquals(CommandHookGuard.Origin.NORMAL, guard.captureOrigin());
    }

    @Test
    void normalWrapperCannotLaunderAnExistingHookContext() {
        CommandHookGuard guard = new CommandHookGuard();
        guard.run(CommandHookGuard.Origin.NORMAL, () -> {
            assertEquals(CommandHookGuard.Origin.COMMAND_HOOK,
                    guard.withOrigin(CommandHookGuard.Origin.NORMAL, guard::captureOrigin));
            assertEquals(CommandHookGuard.Origin.COMMAND_HOOK, guard.captureOrigin());
        });
        assertEquals(CommandHookGuard.Origin.NORMAL, guard.captureOrigin());
    }

    @Test
    void placementFailureRestoresThePreviousOrigin() {
        CommandHookGuard guard = new CommandHookGuard();
        assertThrows(IllegalStateException.class, () -> guard.withOrigin(CommandHookGuard.Origin.COMMAND_HOOK, () -> {
            throw new IllegalStateException("native spawn failed");
        }));
        assertEquals(CommandHookGuard.Origin.NORMAL, guard.captureOrigin());
        assertEquals(42, guard.withOrigin(CommandHookGuard.Origin.NORMAL, () -> 42));
    }

    @Test
    void deferredHookSpawnIsRecordedWithoutStartingAnotherHookChain() {
        CommandHookGuard guard = new CommandHookGuard();
        var pending = new ArrayDeque<CommandHookGuard.Origin>();
        var recorded = new AtomicInteger();
        var announced = new AtomicInteger();
        var hooks = new AtomicInteger();
        pending.add(guard.captureOrigin());
        for (int tick = 0; tick < 10 && !pending.isEmpty(); tick++) {
            var origin = pending.removeFirst();
            recorded.incrementAndGet();
            announced.incrementAndGet();
            guard.run(origin, () -> {
                hooks.incrementAndGet();
                pending.addLast(guard.captureOrigin());
            });
        }
        assertEquals(2, recorded.get());
        assertEquals(2, announced.get());
        assertEquals(1, hooks.get());
        assertTrue(pending.isEmpty());
        assertTrue(guard.run(guard.captureOrigin(), hooks::incrementAndGet));
        assertEquals(2, hooks.get());
    }

    @Test
    void nestedHooksAreRejectedAndExceptionsDoNotPoisonLaterEvents() {
        CommandHookGuard guard = new CommandHookGuard();
        assertThrows(IllegalStateException.class, () -> guard.run(guard.captureOrigin(), () -> {
            assertEquals(CommandHookGuard.Origin.COMMAND_HOOK, guard.captureOrigin());
            assertFalse(guard.run(CommandHookGuard.Origin.NORMAL, () -> fail("Nested hook ran")));
            throw new IllegalStateException("broken command");
        }));
        assertEquals(CommandHookGuard.Origin.NORMAL, guard.captureOrigin());
        assertTrue(guard.run(guard.captureOrigin(), () -> {}));
    }
}
