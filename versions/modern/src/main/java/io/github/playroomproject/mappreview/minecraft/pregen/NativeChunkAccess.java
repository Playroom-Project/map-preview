package io.github.playroomproject.mappreview.minecraft.pregen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/** Minecraft 1.20.2 through 1.20.4 use Either for native chunk completion. */
final class NativeChunkAccess {
    private NativeChunkAccess() { }
    static CompletableFuture<Chunk> full(ServerChunkManager manager, int x, int z) {
        return manager.getChunkFutureSyncOnMainThread(x, z, ChunkStatus.FULL, true).thenApply(result ->
                result.left().orElseThrow(() -> new IllegalStateException("Native chunk loading was cancelled")));
    }
}
