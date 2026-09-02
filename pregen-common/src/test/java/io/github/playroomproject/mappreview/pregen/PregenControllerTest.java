package io.github.playroomproject.mappreview.pregen;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PregenControllerTest {
    private static final ResourceId DIMENSION = new ResourceId("fixture:world");
    private static final Fingerprint WORLD = Fingerprint.builder().add("world-and-generator-content").finish();
    private final AtomicLong time = new AtomicLong(1_000_000_000);

    @Test void generationUsesNativeBudgetAndReleasesEveryTicket() {
        var bridge = new Bridge();
        var controller = controller(bridge, 6, 2, 1);
        controller.start();
        runToCompletion(controller, bridge);
        assertEquals(6, controller.progress().completedChunks());
        assertEquals(6, bridge.closed);
        assertEquals(2, bridge.peak);
        assertTrue(bridge.active.isEmpty());
        assertEquals(1, controller.progress().fraction());
        assertTrue(controller.progress().averageChunksPerSecond() > 0);
        assertEquals(1, bridge.flushes);
    }

    @Test void pauseDrainsBeforeCheckpointAndResumeWaitsForSaveBarrier() {
        var bridge = new Bridge();
        var controller = controller(bridge, 6, 2, 1);
        controller.start(); controller.tick(); controller.pause(); controller.tick();
        assertEquals(PregenState.PAUSING, controller.progress().state());
        assertThrows(IllegalStateException.class, controller::checkpoint);
        bridge.completeAll(); controller.tick();
        assertEquals(PregenState.PAUSED, controller.progress().state());
        assertEquals(2, bridge.submissions.size());
        bridge.save = new CompletableFuture<>();
        var saving = controller.checkpoint().toCompletableFuture();
        assertFalse(saving.isDone());
        assertThrows(IllegalStateException.class, controller::resume);
        bridge.save.complete(null);
        var checkpoint = saving.join();
        assertEquals(2, checkpoint.completedChunks());
        assertEquals(2, checkpoint.cursorOffset());
        controller.resume(); controller.tick();
        assertEquals(new ChunkPos(2, 0), bridge.submissions.get(2));
        runToCompletion(controller, bridge);
    }

    @Test void worldJoinCancelsAdmissionAndWaitsForNativeSave() {
        var bridge = new Bridge();
        var controller = controller(bridge, 8, 2, 1);
        controller.start(); controller.tick(); controller.onWorldJoin(); controller.tick();
        assertEquals(PregenState.CANCELLING, controller.progress().state());
        assertEquals(2, bridge.submissions.size());
        bridge.save = new CompletableFuture<>();
        bridge.completeAll(); controller.tick();
        assertEquals(PregenState.FINISHING, controller.progress().state());
        assertEquals(2, bridge.closed);
        bridge.save.complete(null); controller.tick();
        assertEquals(PregenState.CANCELLED, controller.progress().state());
        assertEquals(2, bridge.submissions.size());
        var checkpoint = controller.checkpoint().toCompletableFuture().join();
        var newBridge = new Bridge();
        var restored = controller(newBridge, 8, 2, 1);
        restored.restore(checkpoint); restored.start(); runToCompletion(restored, newBridge);
        assertEquals(6, newBridge.submissions.size());
        assertFalse(newBridge.submissions.contains(new ChunkPos(0, 0)));
        assertEquals(8, restored.progress().completedChunks());
    }

    @Test void retriesRemainInCheckpointAndCanBeResumedAfterFailure() {
        var bridge = new Bridge();
        var controller = controller(bridge, 1, 1, 1);
        controller.start(); controller.tick();
        bridge.failAll(); controller.tick();
        assertEquals(PregenState.RUNNING, controller.progress().state());
        assertEquals(2, bridge.submissions.size());
        bridge.failAll(); controller.tick();
        assertEquals(PregenState.FAILED, controller.progress().state());
        assertEquals(2, controller.progress().failedAttempts());
        var checkpoint = controller.checkpoint().toCompletableFuture().join();
        assertEquals(List.of(new ChunkPos(0, 0)), checkpoint.pendingChunks());
        var recoveredBridge = new Bridge();
        var recovered = controller(recoveredBridge, 1, 1, 1);
        recovered.restore(checkpoint); recovered.start(); runToCompletion(recovered, recoveredBridge);
        assertEquals(1, recovered.progress().completedChunks());
        assertEquals(1, recoveredBridge.submissions.size());
    }

    @Test void backgroundFutureCompletionDoesNotMutateServerState() {
        var bridge = new Bridge();
        var controller = controller(bridge, 1, 1, 0);
        controller.start(); controller.tick();
        var future = bridge.active.values().iterator().next().future;
        CompletableFuture.runAsync(() -> future.complete(new PregenBridge.ChunkResult(true))).join();
        assertEquals(0, controller.progress().completedChunks());
        assertEquals(0, bridge.closed);
        controller.tick();
        assertEquals(1, controller.progress().completedChunks());
        assertEquals(1, bridge.closed);
    }

    @Test void synchronousNativeFuturesStillRespectPerTickAdmission() {
        var bridge = new Bridge();
        bridge.synchronous = true;
        var controller = controller(bridge, 25, 2, 0);
        controller.start(); controller.tick();
        assertEquals(2, bridge.submissions.size());
        runToCompletion(controller, bridge);
        assertEquals(25, bridge.closed);
        assertTrue(bridge.peak <= 2);
    }

    @Test void saveFailureNeverReportsCompletedWorld() {
        var bridge = new Bridge();
        bridge.save = CompletableFuture.failedFuture(new IllegalStateException("Expected save failure"));
        var controller = controller(bridge, 1, 1, 0);
        controller.start(); controller.tick(); bridge.completeAll(); controller.tick(); controller.tick();
        assertEquals(PregenState.FAILED, controller.progress().state());
        assertTrue(controller.progress().lastFailure().contains("save failure"));
    }

    @Test void listenersRunOnServerThreadAndOneFailureDoesNotHideOthers() {
        var bridge = new Bridge();
        var controller = controller(bridge, 1, 1, 0);
        AtomicInteger notified = new AtomicInteger();
        controller.addListener((dimension, position, result) -> { throw new IllegalArgumentException("Expected integration failure"); });
        controller.addListener((dimension, position, result) -> { bridge.assertServerThread(); notified.incrementAndGet(); });
        controller.start(); runToCompletion(controller, bridge);
        assertEquals(1, notified.get());
    }

    @Test void mismatchedWorldAndShapeCheckpointsAreRejected() {
        var bridge = new Bridge();
        var original = controller(bridge, 2, 1, 0);
        var checkpoint = original.checkpoint().toCompletableFuture().join();
        assertThrows(IllegalArgumentException.class, () -> controller(new Bridge(), 3, 1, 0).restore(checkpoint));
        var differentWorld = new PregenController(new Bridge(), Fingerprint.builder().add("different-world").finish(), DIMENSION,
                plan(2), new PregenSettings(1, 0), time::get);
        assertThrows(IllegalArgumentException.class, () -> differentWorld.restore(checkpoint));
    }

    @Test void serviceCannotBeDrivenFromClientOrWorkerThreads() {
        var controller = controller(new Bridge(), 1, 1, 0);
        CompletableFuture.runAsync(() -> {
            assertThrows(IllegalStateException.class, controller::start);
            assertThrows(IllegalStateException.class, controller::tick);
        }).join();
    }

    @Test void corruptCheckpointCannotSkipUndispatchedChunks() {
        var controller = controller(new Bridge(), 8, 2, 0);
        var valid = controller.checkpoint().toCompletableFuture().join();
        var corrupted = new PregenCheckpoint(1, valid.jobFingerprint(), 7, 0, 0, 0, List.of());
        assertThrows(IllegalArgumentException.class, () -> controller.restore(corrupted));
        var prematureRetry = new PregenCheckpoint(1, valid.jobFingerprint(), 1, 0, 0, 0, List.of(new ChunkPos(2, 0)));
        assertThrows(IllegalArgumentException.class, () -> controller.restore(prematureRetry));
    }

    private PregenController controller(Bridge bridge, int chunks, int inFlight, int retries) {
        return new PregenController(bridge, WORLD, DIMENSION, plan(chunks), new PregenSettings(inFlight, retries), time::get);
    }
    private static ChunkPlan plan(int chunks) { return new ChunkPlan(ChunkAreas.rectangle(new ChunkBounds(0, 0, chunks - 1, 0)), ChunkPlan.Traversal.ROW_MAJOR); }
    private void runToCompletion(PregenController controller, Bridge bridge) {
        for (int i = 0; i < 100 && controller.progress().state() != PregenState.COMPLETED; i++) {
            time.addAndGet(100_000_000);
            bridge.completeAll(); controller.tick();
        }
        assertEquals(PregenState.COMPLETED, controller.progress().state(), controller.progress().toString());
    }

    private static final class Bridge implements PregenBridge {
        private final Thread owner = Thread.currentThread();
        private final Map<ChunkPos, Task> active = new LinkedHashMap<>();
        private final List<ChunkPos> submissions = new ArrayList<>();
        private CompletableFuture<Void> save = CompletableFuture.completedFuture(null);
        private int peak;
        private int closed;
        private int flushes;
        private boolean synchronous;

        @Override public void assertServerThread() {
            if (Thread.currentThread() != owner) { throw new IllegalStateException("Wrong server thread"); }
        }
        @Override public ChunkTask submit(ResourceId dimension, ChunkPos position) {
            assertServerThread();
            assertFalse(active.containsKey(position));
            submissions.add(position);
            var task = new Task(position);
            active.put(position, task);
            peak = Math.max(peak, active.size());
            if (synchronous) { task.future.complete(new ChunkResult(true)); }
            return task;
        }
        @Override public CompletionStage<Void> flush() { assertServerThread(); flushes++; return save; }
        private void completeAll() { List.copyOf(active.values()).forEach(task -> task.future.complete(new ChunkResult(true))); }
        private void failAll() { List.copyOf(active.values()).forEach(task -> task.future.completeExceptionally(new IllegalStateException("Expected chunk failure"))); }

        private final class Task implements ChunkTask {
            private final ChunkPos position;
            private final CompletableFuture<ChunkResult> future = new CompletableFuture<>();
            private Task(ChunkPos position) { this.position = position; }
            @Override public CompletionStage<ChunkResult> completion() { return future; }
            @Override public void close() {
                assertServerThread();
                assertTrue(future.isDone());
                assertSame(this, active.remove(position));
                closed++;
            }
        }
    }
}
