package io.github.playroomproject.mappreview.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.core.api.*;
import io.github.playroomproject.mappreview.core.scheduler.*;
import io.github.playroomproject.mappreview.core.tile.*;
import io.github.playroomproject.mappreview.core.worldgen.*;
import io.github.playroomproject.mappreview.testing.SyntheticWorldgen;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class TileEngineTest {
    private final List<TileEngine> engines = new ArrayList<>();
    private final List<CountDownLatch> gates = new ArrayList<>();

    @AfterEach void releaseWorkers() throws InterruptedException {
        gates.forEach(CountDownLatch::countDown);
        engines.forEach(TileEngine::close);
        for (TileEngine engine : engines) { assertTrue(engine.awaitTermination(Duration.ofSeconds(3))); }
    }

    @Test void duplicateRequestsShareOneComputationAndThenReuseCache() throws Exception {
        var engine = engine(2, 16);
        var backend = blocking(2, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var first = engine.request(session, request(0), priority(0, 0));
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        var second = engine.request(session, request(0), priority(0, 0));
        backend.gate.countDown();
        assertSame(await(first), await(second));
        assertSame(await(first), await(engine.request(session, request(0), priority(0, 0))));
        assertEquals(1, backend.samples.get());
    }

    @Test void anUnknownBackendNeverSamplesConcurrently() throws Exception {
        var engine = engine(4, 16);
        var backend = blocking(1, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var futures = new ArrayList<CompletionStage<TileData>>();
        for (int i = 0; i < 8; i++) { futures.add(engine.request(session, request(i), priority(0, i))); }
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        backend.gate.countDown();
        for (var future : futures) { await(future); }
        assertEquals(1, backend.peak.get());
    }

    @Test void outstandingBudgetIncludesQueuedAndRunningJobs() throws Exception {
        var engine = engine(1, 2);
        var backend = blocking(1, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var a = engine.request(session, request(0), priority(0, 0));
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        var b = engine.request(session, request(1), priority(0, 1));
        var rejected = engine.request(session, request(2), priority(0, 2));
        assertInstanceOf(RejectedExecutionException.class, failure(rejected));
        assertEquals(2, engine.stats().outstanding());
        backend.gate.countDown();
        await(a); await(b);
    }

    @Test void coarseVisibleCenterPrecedesDetailAndPrefetch() throws Exception {
        var engine = engine(1, 16);
        var backend = blocking(1, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var blocker = engine.request(session, request(-1), priority(0, 0));
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        var detail = engine.request(session, request(4), priority(2, 0));
        var prefetch = engine.request(session, request(5), new WorkPriority(true, 0, 0));
        var far = engine.request(session, request(2), priority(0, 100));
        var center = engine.request(session, request(1), priority(0, 0));
        backend.gate.countDown();
        await(blocker); await(detail); await(prefetch); await(far); await(center);
        assertEquals(List.of(-1, 1, 2, 4, 5), backend.order);
    }

    @Test void reseedingRejectsLateResultsWithoutInterruptingTheGenerator() throws Exception {
        var engine = engine(2, 16);
        var backend = blocking(2, context -> context.seed() == 1);
        var old = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var oldFuture = engine.request(old, request(0), priority(0, 0));
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        var current = engine.beginSession(SyntheticWorldgen.context(2), backend);
        assertInstanceOf(CancellationException.class, failure(oldFuture));
        TileData fresh = await(engine.request(current, request(0), priority(0, 0)));
        assertEquals(current.fingerprint(), fresh.key().sessionFingerprint());
        backend.gate.countDown();
        engine.close();
        assertTrue(engine.awaitTermination(Duration.ofSeconds(2)));
        assertNull(engine.cache().get(old.key(request(0))));
        assertEquals(0, backend.interrupted.get());
        assertEquals(backend.opened.get(), backend.closed.get());
    }

    @Test void panningCancelsRunningAndQueuedOffscreenWorkButKeepsVisibleJobs() throws Exception {
        var engine = engine(1, 8);
        var backend = blocking(1, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var oldRunning = engine.request(session, request(0), priority(0, 0));
        assertTrue(backend.started.await(2, TimeUnit.SECONDS));
        var oldQueued = engine.request(session, request(1), priority(0, 1));
        var kept = engine.request(session, request(2), priority(0, 2));
        engine.retainRequests(session, Set.of(session.key(request(2))));
        assertInstanceOf(CancellationException.class, failure(oldRunning));
        assertInstanceOf(CancellationException.class, failure(oldQueued));
        backend.gate.countDown();
        await(kept);
        assertEquals(List.of(0, 2), backend.order);
        assertNull(engine.cache().get(session.key(request(0))));
    }

    @Test void unsupportedLayersNeverReachWorldgen() {
        var engine = engine(2, 8);
        var backend = blocking(2, ignored -> false);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var future = engine.request(session, TileRequest.of(0, 0, 32, DataLayer.CAVE_BLOCKS, -32), priority(0, 0));
        assertInstanceOf(UnsupportedOperationException.class, failure(future));
        assertEquals(0, backend.opened.get());
    }

    @Test void callerCancellingItsFutureDoesNotCancelSharedWork() throws Exception {
        var engine = engine(1, 8);
        var backend = blocking(1, ignored -> true);
        var session = engine.beginSession(SyntheticWorldgen.context(1), backend);
        var a = engine.request(session, request(0), priority(0, 0));
        var b = engine.request(session, request(0), priority(0, 0));
        a.toCompletableFuture().cancel(true);
        backend.gate.countDown();
        assertNotNull(await(b));
        assertEquals(0, backend.interrupted.get());
    }

    @Test void failedJobsCompleteExceptionallyAndDoNotPoisonTheCache() {
        var engine = engine(1, 8);
        BackendFactory broken = new BackendFactory() {
            @Override public ResourceId id() { return new ResourceId("fixture:broken"); }
            @Override public BackendCapabilities capabilities(PreviewContext context) { return SyntheticWorldgen.capabilities(); }
            @Override public WorldgenSampler open(PreviewContext context) { throw new IllegalStateException("Expected test failure"); }
        };
        var session = engine.beginSession(SyntheticWorldgen.context(1), broken);
        assertInstanceOf(IllegalStateException.class, failure(engine.request(session, request(0), priority(0, 0))));
        assertNull(engine.cache().get(session.key(request(0))));
    }

    @Test void closeIsIdempotentAndRejectsFurtherRequests() {
        var engine = engine(1, 8);
        var session = engine.beginSession(SyntheticWorldgen.context(1), SyntheticWorldgen.factory(1));
        engine.close(); engine.close();
        assertThrows(IllegalStateException.class, () -> engine.request(session, request(0), priority(0, 0)));
    }

    private TileEngine engine(int workers, int outstanding) {
        var engine = new TileEngine(new EngineLimits(workers, outstanding, 4 * 1024 * 1024));
        engines.add(engine);
        return engine;
    }

    private BlockingBackend blocking(int parallelism, Predicate<PreviewContext> block) {
        var result = new BlockingBackend(parallelism, block);
        gates.add(result.gate);
        return result;
    }

    private static TileRequest request(int x) { return TileRequest.of(x, 0, 32, DataLayer.BIOMES, 64); }
    private static WorkPriority priority(int pass, double distance) { return new WorkPriority(false, pass, distance); }
    private static TileData await(CompletionStage<TileData> future) throws Exception { return future.toCompletableFuture().get(3, TimeUnit.SECONDS); }
    private static Throwable failure(CompletionStage<TileData> future) {
        Throwable error = assertThrows(Exception.class, () -> await(future));
        while (error.getCause() != null) { error = error.getCause(); }
        return error;
    }

    private static final class BlockingBackend implements BackendFactory {
        private final int parallelism;
        private final Predicate<PreviewContext> block;
        private final CountDownLatch gate = new CountDownLatch(1);
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicInteger samples = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger interrupted = new AtomicInteger();
        private final List<Integer> order = new CopyOnWriteArrayList<>();

        private BlockingBackend(int parallelism, Predicate<PreviewContext> block) { this.parallelism = parallelism; this.block = block; }
        @Override public ResourceId id() { return new ResourceId("fixture:controlled"); }
        @Override public int maximumConcurrency() { return parallelism; }
        @Override public BackendCapabilities capabilities(PreviewContext context) { return SyntheticWorldgen.capabilities(); }
        @Override public WorldgenSampler open(PreviewContext context) {
            opened.incrementAndGet();
            Thread owner = Thread.currentThread();
            return new WorldgenSampler() {
                @Override public TileData sample(TileKey key, PreviewContext ignored, CancellationToken token) {
                    assertSame(owner, Thread.currentThread());
                    samples.incrementAndGet();
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    order.add(key.request().tileX());
                    started.countDown();
                    try {
                        if (block.test(context)) { assertTrue(gate.await(5, TimeUnit.SECONDS)); }
                        return SyntheticWorldgen.sampler(context.seed(), context.biomes().size()).sample(key, context, token);
                    } catch (InterruptedException exception) {
                        interrupted.incrementAndGet();
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    } finally { active.decrementAndGet(); }
                }
                @Override public void close() { assertSame(owner, Thread.currentThread()); closed.incrementAndGet(); }
            };
        }
    }
}
