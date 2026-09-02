package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.concurrent.CompletionStage;

/**
 * Implemented by the logical/integrated server or an optional Chunky provider.
 * All methods except completion callbacks run on the owning server thread.
 * The bridge uses native tickets and scheduling, and composes with C2ME when installed.
 */
public interface PregenBridge {
    void assertServerThread();
    ChunkTask submit(ResourceId dimension, ChunkPos position);
    CompletionStage<Void> flush();

    interface ChunkTask extends AutoCloseable {
        /** Completes only after FULL generation and enrollment in the native save pipeline. */
        CompletionStage<ChunkResult> completion();
        /** Releases the native ticket after completion, on the server thread. Never interrupts worldgen. */
        @Override void close();
    }

    record ChunkResult(boolean newlyGenerated) { }
}
