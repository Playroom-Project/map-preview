package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

/**
 * Server-thread state machine. Background callbacks only enqueue bounded completion messages.
 * Closing a GUI has no effect on this service. Joining the world explicitly cancels generation.
 */
public final class PregenController {
    private final PregenBridge bridge;
    private final ResourceId dimension;
    private final ChunkPlan plan;
    private final PregenSettings settings;
    private final LongSupplier clock;
    private final Fingerprint jobFingerprint;
    private final Map<Long, ActiveChunk> inFlight = new HashMap<>();
    private final ArrayDeque<Attempt> pending = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<CompletedChunk> completions = new ConcurrentLinkedQueue<>();
    private final List<GeneratedChunkListener> listeners = new ArrayList<>();
    private final long[] rateBucketTimes = new long[10];
    private final long[] rateBucketCounts = new long[10];
    private ChunkPlan.Cursor cursor;
    private PregenState state = PregenState.NEW;
    private PregenState afterFlush;
    private CompletableFuture<Void> flushing;
    private CompletableFuture<PregenCheckpoint> checkpointSaving;
    private long completed;
    private long failures;
    private long elapsed;
    private long lastTick;
    private long startedAt;
    private boolean exhausted;
    private String lastFailure = "";

    public PregenController(PregenBridge bridge, Fingerprint worldIdentity, ResourceId dimension,
                            ChunkPlan plan, PregenSettings settings, LongSupplier clock) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        lastTick = clock.getAsLong();
        cursor = plan.cursor(0);
        jobFingerprint = Fingerprint.builder().add("pregen-job-v1").add(worldIdentity.hex())
                .add(dimension.value()).add(plan.fingerprint().hex()).finish();
        java.util.Arrays.fill(rateBucketTimes, Long.MIN_VALUE);
    }

    public void restore(PregenCheckpoint checkpoint) {
        bridge.assertServerThread();
        if (state != PregenState.NEW || !checkpoint.jobFingerprint().equals(jobFingerprint)
                || checkpoint.completedChunks() > plan.totalChunks()
                || checkpoint.completedChunks() + checkpoint.pendingChunks().size() > plan.totalChunks()) {
            throw new IllegalArgumentException("Checkpoint does not match this world, dimension or plan");
        }
        for (ChunkPos position : checkpoint.pendingChunks()) {
            if (plan.indexOf(position) >= checkpoint.cursorOffset()) { throw new IllegalArgumentException("Checkpoint retries a chunk that was never dispatched"); }
        }
        if (plan.acceptedBefore(checkpoint.cursorOffset()) != checkpoint.completedChunks() + checkpoint.pendingChunks().size()) {
            throw new IllegalArgumentException("Checkpoint progress would skip or repeat chunks");
        }
        cursor = plan.cursor(checkpoint.cursorOffset());
        completed = checkpoint.completedChunks();
        failures = checkpoint.failedAttempts();
        elapsed = checkpoint.elapsedNanos();
        pending.clear();
        checkpoint.pendingChunks().forEach(position -> pending.addLast(new Attempt(position, 0)));
    }

    public void addListener(GeneratedChunkListener listener) {
        bridge.assertServerThread();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void start() {
        bridge.assertServerThread();
        if (state != PregenState.NEW || checkpointSaving != null && !checkpointSaving.isDone()) {
            throw new IllegalStateException("Generation has started or its save barrier is still running");
        }
        startedAt = lastTick = clock.getAsLong();
        state = PregenState.RUNNING;
    }

    public void pause() {
        bridge.assertServerThread();
        if (state == PregenState.RUNNING) { state = PregenState.PAUSING; }
    }

    public void resume() {
        bridge.assertServerThread();
        if (state != PregenState.PAUSED || checkpointSaving != null && !checkpointSaving.isDone()) {
            throw new IllegalStateException("Generation is not paused or its save barrier is still running");
        }
        lastTick = clock.getAsLong();
        state = PregenState.RUNNING;
    }

    public void cancel() {
        bridge.assertServerThread();
        if (state == PregenState.RUNNING || state == PregenState.PAUSING || state == PregenState.PAUSED || state == PregenState.NEW) {
            state = PregenState.CANCELLING;
        }
    }

    public void onWorldJoin() { cancel(); }

    /** Called by a native server tick hook; never waits for chunk futures or launches raw worldgen threads. */
    public void tick() {
        bridge.assertServerThread();
        long now = clock.getAsLong();
        if (state == PregenState.RUNNING || state == PregenState.PAUSING || state == PregenState.CANCELLING || state == PregenState.FINISHING) {
            elapsed = Math.addExact(elapsed, Math.max(0, now - lastTick));
        }
        lastTick = now;
        CompletedChunk message;
        while ((message = completions.poll()) != null) { finish(message, now); }
        if (flushing != null && flushing.isDone()) {
            try { flushing.join(); state = afterFlush; }
            catch (CompletionException exception) { lastFailure = describe(exception.getCause()); state = PregenState.FAILED; }
            flushing = null;
        }
        if (state == PregenState.RUNNING) {
            int dispatchBudget = settings.maximumInFlight();
            while (inFlight.size() < settings.maximumInFlight() && dispatchBudget-- > 0 && state == PregenState.RUNNING) {
                Attempt attempt = pending.pollFirst();
                if (attempt == null) {
                    ChunkPos next = cursor.next();
                    if (next == null) { exhausted = true; break; }
                    attempt = new Attempt(next, 0);
                }
                submit(attempt);
            }
        }
        if (inFlight.isEmpty() && flushing == null) {
            if (state == PregenState.PAUSING) { state = PregenState.PAUSED; }
            else if (state == PregenState.CANCELLING) { beginFlush(PregenState.CANCELLED); }
            else if (state == PregenState.RUNNING && exhausted && pending.isEmpty()) {
                beginFlush(completed == plan.totalChunks() ? PregenState.COMPLETED : PregenState.FAILED);
            }
        }
    }

    /** Capture only while quiescent. Persist the returned checkpoint atomically before resuming. */
    public CompletionStage<PregenCheckpoint> checkpoint() {
        bridge.assertServerThread();
        if (!inFlight.isEmpty() || flushing != null || state == PregenState.RUNNING || state == PregenState.PAUSING
                || state == PregenState.CANCELLING || checkpointSaving != null && !checkpointSaving.isDone()) {
            throw new IllegalStateException("Pause and drain generation before saving a checkpoint");
        }
        var checkpoint = new PregenCheckpoint(PregenCheckpoint.SCHEMA_VERSION, jobFingerprint, cursor.offset(), completed,
                failures, elapsed, pending.stream().map(Attempt::position).toList());
        checkpointSaving = bridge.flush().thenApply(ignored -> checkpoint).toCompletableFuture();
        return checkpointSaving.minimalCompletionStage();
    }

    public PregenProgress progress() {
        bridge.assertServerThread();
        long now = clock.getAsLong();
        long bucket = Math.floorDiv(now, 500_000_000L);
        long recent = 0;
        for (int i = 0; i < 10; i++) {
            if (rateBucketTimes[i] <= bucket && rateBucketTimes[i] > bucket - 10) { recent += rateBucketCounts[i]; }
        }
        double seconds = Math.max(0.001, Math.min(5, (now - startedAt) / 1_000_000_000.0));
        return new PregenProgress(state, plan.totalChunks(), completed, failures, inFlight.size(), recent / seconds,
                elapsed == 0 ? 0 : completed * 1_000_000_000.0 / elapsed, elapsed, lastFailure);
    }

    private void submit(Attempt attempt) {
        PregenBridge.ChunkTask task;
        try { task = Objects.requireNonNull(bridge.submit(dimension, attempt.position()), "chunk task"); }
        catch (RuntimeException exception) { retry(attempt, exception); return; }
        var active = new ActiveChunk(attempt, task);
        inFlight.put(attempt.position().packed(), active);
        try {
            Objects.requireNonNull(task.completion(), "chunk completion").whenComplete((result, error) ->
                    completions.add(new CompletedChunk(active, result, error)));
        } catch (RuntimeException exception) {
            completions.add(new CompletedChunk(active, null, exception));
        }
    }

    private void finish(CompletedChunk message, long now) {
        ActiveChunk active = message.active();
        if (!inFlight.remove(active.attempt().position().packed(), active)) { return; }
        Throwable error = message.error();
        boolean releaseFailed = false;
        try { active.task().close(); }
        catch (RuntimeException exception) {
            releaseFailed = true;
            if (error == null) { error = exception; } else if (error != exception) { error.addSuppressed(exception); }
        }
        if (error != null || message.result() == null) {
            retry(active.attempt(), error != null ? error : new IllegalStateException("Missing chunk result"));
            if (releaseFailed) { state = PregenState.FAILED; }
            return;
        }
        completed++;
        long bucket = Math.floorDiv(now, 500_000_000L);
        int index = Math.floorMod(bucket, 10);
        if (rateBucketTimes[index] != bucket) { rateBucketTimes[index] = bucket; rateBucketCounts[index] = 0; }
        rateBucketCounts[index]++;
        for (GeneratedChunkListener listener : listeners) {
            try { listener.completed(dimension, active.attempt().position(), message.result()); }
            catch (RuntimeException exception) {
                MapPreView.LOGGER.log(System.Logger.Level.WARNING, "Map PreView chunk integration failed", exception);
            }
        }
    }

    private void retry(Attempt attempt, Throwable error) {
        failures++;
        lastFailure = describe(error);
        pending.addLast(new Attempt(attempt.position(), attempt.retries() + 1));
        if (attempt.retries() >= settings.maximumRetries()) { state = PregenState.FAILED; }
    }

    private void beginFlush(PregenState target) {
        state = PregenState.FINISHING;
        afterFlush = target;
        try { flushing = Objects.requireNonNull(bridge.flush(), "save barrier").toCompletableFuture(); }
        catch (RuntimeException exception) { state = PregenState.FAILED; lastFailure = describe(exception); }
    }

    private static String describe(Throwable error) { return error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), ""); }
    private record Attempt(ChunkPos position, int retries) { }
    private record ActiveChunk(Attempt attempt, PregenBridge.ChunkTask task) { }
    private record CompletedChunk(ActiveChunk active, PregenBridge.ChunkResult result, Throwable error) { }
}
