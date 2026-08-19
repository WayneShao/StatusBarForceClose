package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class BridgeRequestDispatcherTest {
    private static final int MODULE_UID = 10300;
    private static final int SYSTEM_UI_UID = 10042;
    private static final CallerIdentity MODULE = new CallerIdentity(
            MODULE_UID, new String[] {"com.wayne.statusbarforceclose"});
    private static final CallerIdentity SYSTEM_UI = new CallerIdentity(
            SYSTEM_UI_UID, new String[] {"com.android.systemui"});
    private static final CallerIdentity ATTACKER = new CallerIdentity(
            10999, new String[] {"com.example.attacker"});

    @Test
    public void registeredSystemUiGetsOpaqueSessionAndInitialConfiguration() {
        Fixture fixture = fixture();
        List<ForceStopConfiguration> delivered = new ArrayList<>();

        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", delivered::add);

        assertEquals(BridgeProtocol.Status.OK, registration.status());
        assertNotNull(registration.sessionToken());
        assertEquals(fixture.repository.snapshot.configuration(), registration.configuration());
        assertEquals(List.of(fixture.repository.snapshot.configuration()), delivered);
        assertTrue(registration.newGeneration());
    }

    @Test
    public void registeredSystemUiGetsInitialAndChangedRootRuntimeState() {
        Fixture fixture = fixture();
        List<BridgeRuntimeState> runtimeStates = new ArrayList<>();

        fixture.dispatcher.registerSystemUi(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                ignored -> { },
                runtimeStates::add);
        fixture.root.state = RootConnectionState.TRANSIENT_ERROR;
        fixture.dispatcher.onRootStateChanged();

        assertEquals(RootConnectionState.CONNECTED, runtimeStates.get(0).rootState());
        assertEquals(RootConnectionState.TRANSIENT_ERROR,
                runtimeStates.get(runtimeStates.size() - 1).rootState());
    }

    @Test
    public void forceStopRequiresSystemUiIdentityAndLiveMatchingSession() {
        Fixture fixture = fixture();
        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", ignored -> { });

        for (CallerIdentity caller : List.of(MODULE, ATTACKER)) {
            BackendResult rejected = fixture.dispatcher.forceStopRoot(
                    caller,
                    BridgeProtocol.VERSION,
                    "generation-a",
                    registration.sessionToken(),
                    "com.example.reader",
                    10,
                    true);
            assertEquals(BackendStatus.PERMISSION_REJECTED, rejected.status());
        }
        BackendResult stale = fixture.dispatcher.forceStopRoot(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                "stale-token",
                "com.example.reader",
                10,
                true);
        assertEquals(BackendStatus.PERMISSION_REJECTED, stale.status());
        assertEquals(0, fixture.root.forceStops.get());

        BackendResult accepted = fixture.dispatcher.forceStopRoot(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                registration.sessionToken(),
                "com.example.reader",
                10,
                true);
        assertEquals(BackendStatus.SUCCESS, accepted.status());
        assertEquals(1, fixture.root.forceStops.get());
    }

    @Test
    public void systemUiRuntimeStateRequiresLiveMatchingSession() {
        Fixture fixture = fixture();
        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", ignored -> { });

        BridgeRuntimeState accepted = fixture.dispatcher.getSystemUiRuntimeState(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                registration.sessionToken());
        assertEquals(RootConnectionState.CONNECTED, accepted.rootState());
        assertTrue(accepted.systemUiConnected());

        for (CallerIdentity caller : List.of(MODULE, ATTACKER)) {
            BridgeRuntimeState rejected = fixture.dispatcher.getSystemUiRuntimeState(
                    caller,
                    BridgeProtocol.VERSION,
                    "generation-a",
                    registration.sessionToken());
            assertEquals(RootConnectionState.INCOMPATIBLE, rejected.rootState());
            assertFalse(rejected.systemUiConnected());
        }
        BridgeRuntimeState stale = fixture.dispatcher.getSystemUiRuntimeState(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                "stale-token");
        assertEquals(RootConnectionState.INCOMPATIBLE, stale.rootState());
        assertFalse(stale.systemUiConnected());
    }

    @Test
    public void wrongProtocolAndInvalidPayloadFailBeforeBackends() {
        Fixture fixture = fixture();
        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", ignored -> { });

        assertEquals(BackendStatus.UNSUPPORTED,
                fixture.dispatcher.forceStopRoot(
                        SYSTEM_UI,
                        BridgeProtocol.VERSION + 1,
                        "generation-a",
                        registration.sessionToken(),
                        "com.example.reader",
                        0,
                        false).status());
        assertEquals(BackendStatus.OPERATION_FAILED,
                fixture.dispatcher.forceStopRoot(
                        SYSTEM_UI,
                        BridgeProtocol.VERSION,
                        "generation-a",
                        registration.sessionToken(),
                        "",
                        -1,
                        false).status());
        assertEquals(0, fixture.root.forceStops.get());
    }

    @Test
    public void moduleConfigWriteCommitsThenPushesStrictlyNewerRevision() {
        Fixture fixture = fixture();
        List<ForceStopConfiguration> delivered = new ArrayList<>();
        fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", delivered::add);
        int commitsBeforeUpdate = fixture.repository.commits.get();

        BridgeProtocol.Status denied = fixture.dispatcher.updateConfiguration(
                ATTACKER, BridgeProtocol.VERSION, ExecutionMode.ROOT_ONLY, false);
        BridgeProtocol.Status updated = fixture.dispatcher.updateConfiguration(
                MODULE, BridgeProtocol.VERSION, ExecutionMode.ROOT_ONLY, false);

        assertEquals(BridgeProtocol.Status.PERMISSION_REJECTED, denied);
        assertEquals(BridgeProtocol.Status.OK, updated);
        assertEquals(2, delivered.size());
        assertEquals(ExecutionMode.ROOT_ONLY,
                fixture.repository.snapshot.configuration().executionMode());
        assertEquals(2L, fixture.repository.snapshot.configuration().revision());
        assertEquals(commitsBeforeUpdate + 1, fixture.repository.commits.get());
    }

    @Test
    public void freshSettingsTokenAndRecoveryAreAuthorizedAndIdempotent() {
        Fixture fixture = fixture();
        assertEquals(BridgeProtocol.Status.OK, fixture.dispatcher.requestRootForSettings(
                MODULE, BridgeProtocol.VERSION, "settings-a"));
        assertEquals(BridgeProtocol.Status.OK, fixture.dispatcher.requestRootForSettings(
                MODULE, BridgeProtocol.VERSION, "settings-a"));
        assertEquals(1, fixture.root.freshRequests.get());

        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", ignored -> { });
        assertEquals(BridgeProtocol.Status.OK, fixture.dispatcher.requestRecovery(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                registration.sessionToken(),
                RecoveryReason.TASK_FRONT,
                1L));
        assertEquals(BridgeProtocol.Status.OK, fixture.dispatcher.requestRecovery(
                SYSTEM_UI,
                BridgeProtocol.VERSION,
                "generation-a",
                registration.sessionToken(),
                RecoveryReason.TASK_FRONT,
                1L));
        assertEquals(1, fixture.root.recoveries.get());
    }

    @Test
    public void moduleObserverAndOptimizationStayModuleOnly() {
        Fixture fixture = fixture();
        List<BridgeRuntimeState> observed = new ArrayList<>();
        assertEquals(BridgeProtocol.Status.OK, fixture.dispatcher.registerRuntimeObserver(
                MODULE, BridgeProtocol.VERSION, "observer-a", observed::add));
        assertEquals(1, observed.size());
        assertEquals(BridgeProtocol.Status.PERMISSION_REJECTED,
                fixture.dispatcher.setBackgroundOptimization(
                        ATTACKER, BridgeProtocol.VERSION, false));
        assertEquals(BridgeProtocol.Status.OK,
                fixture.dispatcher.setBackgroundOptimization(
                        MODULE, BridgeProtocol.VERSION, false));
        assertEquals(1, fixture.optimization.requests.get());
        assertFalse(fixture.optimization.lastEnabled);
        assertTrue(observed.size() >= 2);
        assertTrue(fixture.dispatcher.unregisterRuntimeObserver(
                MODULE, BridgeProtocol.VERSION, "observer-a"));
    }

    @Test
    public void callbackDeathInvalidatesSessionAndLegacyEntryDoesNotExist() {
        Fixture fixture = fixture();
        SystemUiRegistration registration = fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", ignored -> { });
        assertTrue(fixture.dispatcher.onSystemUiCallbackDied(registration.callbackId()));
        assertEquals(BackendStatus.PERMISSION_REJECTED,
                fixture.dispatcher.forceStopRoot(
                        SYSTEM_UI,
                        BridgeProtocol.VERSION,
                        "generation-a",
                        registration.sessionToken(),
                        "com.example.reader",
                        0,
                        false).status());
        assertEquals(0, fixture.root.forceStops.get());
        assertNull(findLegacyForceStopMethod());
    }

    @Test
    public void replacementRegistrationStopsDeliveringToOldCallback() {
        Fixture fixture = fixture();
        List<ForceStopConfiguration> oldCallback = new ArrayList<>();
        List<ForceStopConfiguration> replacement = new ArrayList<>();
        fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", oldCallback::add);
        fixture.dispatcher.registerSystemUi(
                SYSTEM_UI, BridgeProtocol.VERSION, "generation-a", replacement::add);

        fixture.dispatcher.updateConfiguration(
                MODULE, BridgeProtocol.VERSION, ExecutionMode.ROOT_ONLY, true);

        assertEquals(1, oldCallback.size());
        assertEquals(2, replacement.size());
    }

    @Test
    public void configurationWritePreservesExternallyUpdatedOptimizationJournal() {
        Fixture fixture = fixture();
        OptimizationJournal externallyUpdated = OptimizationJournal.initial()
                .startGeneration()
                .putItem(
                        OptimizationItem.DOZE_WHITELIST,
                        new OptimizationItemState(
                                0, 1, true, OptimizationResolution.APPLIED))
                .withPhase(OptimizationPhase.ACTIVE);
        fixture.repository.snapshot = new BridgeStateSnapshot(
                fixture.repository.snapshot.configuration(),
                fixture.repository.snapshot.rootJournal(),
                externallyUpdated);

        fixture.dispatcher.updateConfiguration(
                MODULE, BridgeProtocol.VERSION, ExecutionMode.ROOT_ONLY, true);

        assertEquals(externallyUpdated, fixture.repository.snapshot.optimizationJournal());
    }

    private static java.lang.reflect.Method findLegacyForceStopMethod() {
        try {
            return BridgeRequestDispatcher.class.getDeclaredMethod(
                    "forceStop", String.class, int.class);
        } catch (NoSuchMethodException expected) {
            return null;
        }
    }

    private static Fixture fixture() {
        FakeRepository repository = new FakeRepository(new BridgeStateSnapshot(
                ForceStopConfiguration.bridgeDefaults(),
                RootAttemptJournal.initial(3L)));
        FakeRootOperations root = new FakeRootOperations();
        FakeOptimizationOperations optimization = new FakeOptimizationOperations();
        AtomicInteger tokens = new AtomicInteger();
        BridgeRequestDispatcher dispatcher = new BridgeRequestDispatcher(
                MODULE_UID,
                repository,
                root,
                optimization,
                new SystemUiSessionRegistry(
                        BridgeProtocol.VERSION,
                        () -> "token-" + tokens.incrementAndGet()));
        return new Fixture(dispatcher, repository, root, optimization);
    }

    private record Fixture(
            BridgeRequestDispatcher dispatcher,
            FakeRepository repository,
            FakeRootOperations root,
            FakeOptimizationOperations optimization) {
    }

    private static final class FakeRepository implements BridgeStateRepository {
        private BridgeStateSnapshot snapshot;
        private final AtomicInteger commits = new AtomicInteger();

        FakeRepository(BridgeStateSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public BridgeStateSnapshot load() {
            return snapshot;
        }

        @Override
        public boolean commit(BridgeStateSnapshot newSnapshot) {
            snapshot = newSnapshot;
            commits.incrementAndGet();
            return true;
        }
    }

    private static final class FakeRootOperations implements RootOperations {
        private final AtomicInteger forceStops = new AtomicInteger();
        private final AtomicInteger freshRequests = new AtomicInteger();
        private final AtomicInteger recoveries = new AtomicInteger();
        private RootConnectionState state = RootConnectionState.CONNECTED;

        @Override
        public RootConnectionState state() {
            return state;
        }

        @Override
        public BackendResult forceStop(String packageName, int userId, boolean wait) {
            forceStops.incrementAndGet();
            return new BackendResult(BackendKind.ROOT, BackendStatus.SUCCESS, 4L);
        }

        @Override
        public void requestFreshConnection() {
            freshRequests.incrementAndGet();
        }

        @Override
        public void requestRecovery(RecoveryReason reason, long windowId) {
            recoveries.incrementAndGet();
        }
    }

    private static final class FakeOptimizationOperations implements OptimizationOperations {
        private final AtomicInteger requests = new AtomicInteger();
        private boolean lastEnabled;

        @Override
        public boolean setEnabled(boolean enabled) {
            requests.incrementAndGet();
            lastEnabled = enabled;
            return true;
        }
    }
}
