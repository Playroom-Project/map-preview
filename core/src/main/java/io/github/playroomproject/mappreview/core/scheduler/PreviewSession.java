package io.github.playroomproject.mappreview.core.scheduler;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import io.github.playroomproject.mappreview.core.worldgen.BackendCapabilities;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** An immutable generation epoch with a cooperative lifetime token. */
public final class PreviewSession implements CancellationToken {
    private final long epoch;
    private final PreviewContext context;
    private final BackendFactory backend;
    private final BackendCapabilities capabilities;
    private final Fingerprint fingerprint;
    final Semaphore permits;
    volatile boolean cancelled;

    PreviewSession(long epoch, PreviewContext context, BackendFactory backend, int workers) {
        this.epoch = epoch;
        this.context = Objects.requireNonNull(context, "context");
        this.backend = Objects.requireNonNull(backend, "backend");
        capabilities = Objects.requireNonNull(backend.capabilities(context), "capabilities");
        int parallelism = backend.maximumConcurrency();
        if (parallelism < 1 || backend.dataVersion() < 1) {
            throw new IllegalArgumentException("Backend concurrency and data version must be positive");
        }
        permits = new Semaphore(Math.min(workers, parallelism), true);
        fingerprint = Fingerprint.builder().add(context.fingerprint().hex())
                .add(backend.id().value()).add(backend.dataVersion()).finish();
    }

    public long epoch() { return epoch; }
    public PreviewContext context() { return context; }
    public BackendFactory backend() { return backend; }
    public BackendCapabilities capabilities() { return capabilities; }
    public Fingerprint fingerprint() { return fingerprint; }
    public TileKey key(TileRequest request) { return new TileKey(fingerprint, request); }
    @Override public boolean isCancelled() { return cancelled; }
}
