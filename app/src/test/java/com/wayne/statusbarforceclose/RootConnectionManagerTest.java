package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class RootConnectionManagerTest {
    @Test
    public void startsOneBindPerAttemptAndIgnoresRepeatedWarmup() {
        Fixture fixture = fixture();

        fixture.manager.requestFreshConnection();
        fixture.manager.requestFreshConnection();

        assertEquals(RootConnectionState.CONNECTING, fixture.manager.state());
        assertEquals(1, fixture.adapter.bindings.size());
        assertEquals(1, fixture.scheduler.tasks.size());
    }

    @Test
    public void tenSecondTimeoutInvalidatesAndUnbindsOnMainExecutor() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeBinding binding = fixture.adapter.bindings.get(0);

        fixture.clock.now = 9_999L;
        fixture.scheduler.runDue();
        assertEquals(0, binding.unbinds.get());
        fixture.clock.now = 10_000L;
        fixture.scheduler.runDue();

        assertEquals(RootConnectionState.TRANSIENT_ERROR, fixture.manager.state());
        assertEquals(1, fixture.main.executions.get());
        assertEquals(1, binding.unbinds.get());
    }

    @Test
    public void staleLateCallbackIsDisposedAndCannotReplaceNewAttempt() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeBinding stale = fixture.adapter.bindings.get(0);
        fixture.clock.now = 10_000L;
        fixture.scheduler.runDue();

        fixture.clock.now = 10_001L;
        fixture.manager.requestRecovery(RecoveryReason.TASK_FRONT, 1L);
        FakeBinding current = fixture.adapter.bindings.get(1);
        FakeController staleController = new FakeController();
        stale.listener.connected(staleController);

        assertEquals(2, fixture.main.executions.get());
        assertEquals(2, stale.unbinds.get());
        assertEquals(RootConnectionState.CONNECTING, fixture.manager.state());
        FakeController currentController = new FakeController();
        current.listener.connected(currentController);
        assertEquals(RootConnectionState.CONNECTED, fixture.manager.state());
        assertSame(currentController, fixture.manager.connectedControllerForTest());
    }

    @Test
    public void aliveConnectedBinderIsReusedForForceStop() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeController controller = new FakeController();
        fixture.adapter.bindings.get(0).listener.connected(controller);

        BackendResult result = fixture.manager.forceStop("com.example.reader", 999, false);

        assertEquals(BackendStatus.SUCCESS, result.status());
        assertEquals(1, controller.forceStops.get());
        assertEquals("com.example.reader", controller.lastPackage);
        assertEquals(999, controller.lastUserId);
        assertEquals(1, fixture.adapter.bindings.size());
    }

    @Test
    public void binderDeathDisconnectsAndOneRecoveryStartsNewAttempt() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeBinding first = fixture.adapter.bindings.get(0);
        first.listener.connected(new FakeController());
        first.listener.disconnected();

        assertEquals(RootConnectionState.DISCONNECTED, fixture.manager.state());
        fixture.manager.requestRecovery(RecoveryReason.USER_PRESENT, 2L);
        fixture.manager.requestRecovery(RecoveryReason.TASK_FRONT, 2L);
        assertEquals(2, fixture.adapter.bindings.size());
    }

    @Test
    public void nullBinderIsIncompatibleAndDoesNotLoop() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeBinding binding = fixture.adapter.bindings.get(0);
        binding.listener.connected(null);

        assertEquals(RootConnectionState.INCOMPATIBLE, fixture.manager.state());
        assertEquals(List.of(RootConnectionState.INCOMPATIBLE), fixture.terminals);
        fixture.manager.requestFreshConnection();
        fixture.manager.requestRecovery(RecoveryReason.TASK_FRONT, 1L);
        assertEquals(1, fixture.adapter.bindings.size());
    }

    @Test
    public void explicitDenialIsTerminalButUnknownFailureIsTransient() {
        Fixture denied = fixture();
        denied.manager.requestFreshConnection();
        denied.adapter.bindings.get(0).listener.denied();
        assertEquals(RootConnectionState.DENIED, denied.manager.state());
        assertEquals(List.of(RootConnectionState.DENIED), denied.terminals);

        Fixture unknown = fixture();
        unknown.manager.requestFreshConnection();
        unknown.adapter.bindings.get(0).listener.failed(new IllegalStateException("transport"));
        assertEquals(RootConnectionState.TRANSIENT_ERROR, unknown.manager.state());
        assertTrue(unknown.terminals.isEmpty());
    }

    @Test
    public void authorizedFreshTriggerClearsDeniedButNotIncompatible() {
        Fixture denied = fixture();
        denied.manager.requestFreshConnection();
        denied.adapter.bindings.get(0).listener.denied();
        denied.manager.requestFreshConnection();
        assertEquals(RootConnectionState.CONNECTING, denied.manager.state());
        assertEquals(2, denied.adapter.bindings.size());

        Fixture incompatible = fixture();
        incompatible.manager.requestFreshConnection();
        incompatible.adapter.bindings.get(0).listener.incompatible();
        incompatible.manager.requestFreshConnection();
        assertEquals(RootConnectionState.INCOMPATIBLE, incompatible.manager.state());
        assertEquals(1, incompatible.adapter.bindings.size());
    }

    @Test
    public void operationFailureDoesNotClaimSuccess() {
        Fixture fixture = fixture();
        fixture.manager.requestFreshConnection();
        FakeController controller = new FakeController();
        controller.forceStopResult = false;
        fixture.adapter.bindings.get(0).listener.connected(controller);

        BackendResult result = fixture.manager.forceStop("com.example.reader", 0, false);

        assertFalse(result.isSuccess());
        assertEquals(BackendStatus.OPERATION_FAILED, result.status());
    }

    private static Fixture fixture() {
        FakeClock clock = new FakeClock();
        FakeScheduler scheduler = new FakeScheduler(clock);
        FakeMainExecutor main = new FakeMainExecutor();
        FakeRootBindAdapter adapter = new FakeRootBindAdapter();
        List<RootConnectionState> terminals = new ArrayList<>();
        RootConnectionManager manager = new RootConnectionManager(
                adapter,
                clock,
                scheduler,
                main,
                terminals::add,
                10_000L);
        return new Fixture(manager, adapter, clock, scheduler, main, terminals);
    }

    private record Fixture(
            RootConnectionManager manager,
            FakeRootBindAdapter adapter,
            FakeClock clock,
            FakeScheduler scheduler,
            FakeMainExecutor main,
            List<RootConnectionState> terminals) {
    }

    private static final class FakeClock implements MonotonicClock {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }
    }

    private static final class FakeScheduler implements DeadlineScheduler {
        private final FakeClock clock;
        private final List<Task> tasks = new ArrayList<>();

        FakeScheduler(FakeClock clock) {
            this.clock = clock;
        }

        @Override
        public void schedule(long deadlineMillis, Runnable task) {
            tasks.add(new Task(deadlineMillis, task));
        }

        void runDue() {
            List<Task> snapshot = new ArrayList<>(tasks);
            for (Task task : snapshot) {
                if (clock.now >= task.deadlineMillis) {
                    tasks.remove(task);
                    task.runnable.run();
                }
            }
        }

        private record Task(long deadlineMillis, Runnable runnable) {
        }
    }

    private static final class FakeMainExecutor implements MainThreadExecutor {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public void execute(Runnable action) {
            executions.incrementAndGet();
            action.run();
        }
    }

    private static final class FakeRootBindAdapter implements RootBindAdapter {
        private final List<FakeBinding> bindings = new ArrayList<>();

        @Override
        public Binding bind(Listener listener) {
            FakeBinding binding = new FakeBinding(listener);
            bindings.add(binding);
            return binding;
        }
    }

    private static final class FakeBinding implements RootBindAdapter.Binding {
        private final RootBindAdapter.Listener listener;
        private final AtomicInteger unbinds = new AtomicInteger();

        FakeBinding(RootBindAdapter.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void unbind() {
            unbinds.incrementAndGet();
        }
    }

    private static final class FakeController implements RootController {
        private final AtomicInteger forceStops = new AtomicInteger();
        private boolean alive = true;
        private boolean forceStopResult = true;
        private String lastPackage;
        private int lastUserId;

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public boolean forceStop(String packageName, int userId) {
            forceStops.incrementAndGet();
            lastPackage = packageName;
            lastUserId = userId;
            return forceStopResult;
        }
    }
}
