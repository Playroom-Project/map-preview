package io.github.playroomproject.mappreview.core.scheduler;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.cache.TileCache;
import io.github.playroomproject.mappreview.core.tile.TileData;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import io.github.playroomproject.mappreview.core.worldgen.WorldgenSampler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One engine per preview surface. All submissions are nonblocking and deduplicated.
 * A semaphore bounds the entire executor, including running jobs, despite the priority queue type.
 * Futures carry CPU data only; render adapters must still reject old epochs at upload time.
 */
public final class TileEngine implements AutoCloseable {
    private final Object lock = new Object();
    private final EngineLimits limits;
    private final TileCache cache;
    private final Semaphore slots;
    private final ThreadPoolExecutor executor;
    private final ThreadLocal<WorkerState> workerState = ThreadLocal.withInitial(WorkerState::new);
    private final Map<TileKey, Job> jobs = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private long epoch;
    private PreviewSession active;
    private boolean closed;

    public TileEngine(EngineLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        cache = new TileCache(limits.cacheBytes());
        slots = new Semaphore(limits.maximumOutstandingTasks());
        AtomicInteger threadIds = new AtomicInteger();
        executor = new ThreadPoolExecutor(limits.workers(), limits.workers(), 0, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(() -> {
                        try { runnable.run(); }
                        finally {
                            workerState.get().close();
                            workerState.remove();
                        }
                    }, MapPreView.NAME + " worker " + threadIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.prestartAllCoreThreads();
    }

    public PreviewSession beginSession(PreviewContext context, BackendFactory backend) {
        List<Job> obsolete;
        PreviewSession session;
        synchronized (lock) {
            ensureOpen();
            session = new PreviewSession(epoch + 1, context, backend, limits.workers());
            if (active != null) { active.cancelled = true; }
            active = session;
            epoch++;
            obsolete = cancelMatching(null);
        }
        obsolete.forEach(Job::completeCancellation);
        return session;
    }

    public CompletionStage<TileData> request(PreviewSession session, TileRequest request, WorkPriority priority) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(priority, "priority");
        synchronized (lock) {
            ensureOpen();
            if (session != active || session.isCancelled()) {
                return failedStage(new CancellationException("Map PreView session is obsolete"));
            }
            if (!session.capabilities().supports(request.layer())) {
                return failedStage(new UnsupportedOperationException(session.capabilities().support(request.layer()).explanation()));
            }
            TileKey key = session.key(request);
            TileData cached = cache.get(key);
            if (cached != null) { return CompletableFuture.completedFuture(cached).minimalCompletionStage(); }
            Job existing = jobs.get(key);
            if (existing != null) {
                if (priority.compareTo(existing.priority) < 0 && executor.remove(existing)) {
                    existing.priority = priority;
                    executor.execute(existing);
                }
                return existing.future.minimalCompletionStage();
            }
            if (!slots.tryAcquire()) {
                rejected.incrementAndGet();
                return failedStage(new RejectedExecutionException("Map PreView work budget is full; retry visible tiles later"));
            }
            Job job = new Job(session, key, priority, sequence.getAndIncrement());
            jobs.put(key, job);
            executor.execute(job);
            return job.future.minimalCompletionStage();
        }
    }

    /** Cancel obsolete viewport work without discarding reusable data or changing the seed epoch. */
    public void retainRequests(PreviewSession session, Set<TileKey> retained) {
        Objects.requireNonNull(retained, "retained");
        List<Job> obsolete;
        synchronized (lock) {
            if (session != active || closed) { return; }
            obsolete = cancelMatching(retained);
        }
        obsolete.forEach(Job::completeCancellation);
    }

    public TileCache cache() { return cache; }

    public Stats stats() {
        return new Stats(completed.get(), cancelled.get(), failed.get(), rejected.get(),
                limits.maximumOutstandingTasks() - slots.availablePermits(), executor.getQueue().size());
    }

    /** Initiates cooperative shutdown. Waiting is explicit so closing a screen never blocks rendering. */
    @Override public void close() {
        List<Job> obsolete;
        synchronized (lock) {
            if (closed) { return; }
            closed = true;
            if (active != null) { active.cancelled = true; }
            obsolete = cancelMatching(null);
            executor.shutdown();
        }
        obsolete.forEach(Job::completeCancellation);
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        if (Thread.currentThread().getName().startsWith(MapPreView.NAME + " worker ")) {
            throw new IllegalStateException("A worker cannot wait for its own termination");
        }
        return executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private List<Job> cancelMatching(Set<TileKey> retained) {
        var obsolete = new ArrayList<Job>();
        var iterator = jobs.values().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (retained == null || !retained.contains(job.key)) {
                job.obsolete = true;
                if (executor.remove(job)) { slots.release(); }
                iterator.remove();
                obsolete.add(job);
            }
        }
        return obsolete;
    }

    private void ensureOpen() { if (closed) { throw new IllegalStateException("Map PreView engine is closed"); } }
    private static CompletionStage<TileData> failedStage(Throwable exception) {
        return CompletableFuture.<TileData>failedFuture(exception).minimalCompletionStage();
    }

    public record Stats(long completed, long cancelled, long failed, long rejected, int outstanding, int queued) { }

    private final class Job implements Runnable, Comparable<Job>, CancellationToken {
        private final PreviewSession session;
        private final TileKey key;
        private final long order;
        private final CompletableFuture<TileData> future = new CompletableFuture<>();
        private WorkPriority priority;
        private volatile boolean obsolete;

        private Job(PreviewSession session, TileKey key, WorkPriority priority, long order) {
            this.session = session;
            this.key = key;
            this.priority = priority;
            this.order = order;
        }

        @Override public boolean isCancelled() { return obsolete || session.isCancelled(); }
        @Override public int compareTo(Job other) {
            int result = priority.compareTo(other.priority);
            return result != 0 ? result : Long.compare(order, other.order);
        }

        private void completeCancellation() {
            if (future.completeExceptionally(new CancellationException("Map PreView tile is obsolete"))) {
                cancelled.incrementAndGet();
            }
        }

        @Override public void run() {
            boolean permit = false;
            try {
                check();
                while (!(permit = session.permits.tryAcquire(25, TimeUnit.MILLISECONDS))) { check(); }
                check();
                WorldgenSampler sampler = workerState.get().sampler(session);
                check();
                TileData result = Objects.requireNonNull(sampler.sample(key, session.context(), this), "sample result");
                if (!key.equals(result.key())) { throw new IllegalStateException("Backend returned a different tile key"); }
                synchronized (lock) {
                    check();
                    cache.put(result);
                }
                if (future.complete(result)) { completed.incrementAndGet(); }
            } catch (CancellationException exception) {
                completeCancellation();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                completeCancellation();
            } catch (Throwable exception) {
                if (future.completeExceptionally(exception)) { failed.incrementAndGet(); }
                if (exception instanceof Error error) { throw error; }
            } finally {
                if (permit) { session.permits.release(); }
                synchronized (lock) { jobs.remove(key, this); }
                slots.release();
            }
        }
    }

    private static final class WorkerState {
        private PreviewSession session;
        private WorldgenSampler sampler;

        private WorldgenSampler sampler(PreviewSession requested) {
            if (session != requested) {
                close();
                sampler = Objects.requireNonNull(requested.backend().open(requested.context()), "backend sampler");
                session = requested;
            }
            return sampler;
        }

        private void close() {
            WorldgenSampler previous = sampler;
            sampler = null;
            session = null;
            if (previous != null) {
                try { previous.close(); }
                catch (RuntimeException exception) {
                    MapPreView.LOGGER.log(System.Logger.Level.WARNING, "Map PreView could not release a backend sampler", exception);
                }
            }
        }
    }
}
