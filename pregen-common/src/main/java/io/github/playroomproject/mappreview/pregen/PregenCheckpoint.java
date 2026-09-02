package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Written only after native work drains and the server save barrier completes. */
public record PregenCheckpoint(int schemaVersion, Fingerprint jobFingerprint, long cursorOffset,
                               long completedChunks, long failedAttempts, long elapsedNanos, List<ChunkPos> pendingChunks) {
    public static final int SCHEMA_VERSION = 1;
    public PregenCheckpoint {
        Objects.requireNonNull(jobFingerprint, "jobFingerprint");
        pendingChunks = List.copyOf(pendingChunks);
        if (schemaVersion != SCHEMA_VERSION || cursorOffset < 0 || completedChunks < 0 || failedAttempts < 0
                || elapsedNanos < 0 || pendingChunks.size() > 256 || new HashSet<>(pendingChunks).size() != pendingChunks.size()) {
            throw new IllegalArgumentException("Invalid Map PreView pregeneration checkpoint");
        }
    }
}
