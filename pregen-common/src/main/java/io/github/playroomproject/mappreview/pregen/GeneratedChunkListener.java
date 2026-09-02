package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.api.ResourceId;

/** Optional map and LOD adapters receive completed chunks through their documented public APIs. */
@FunctionalInterface
public interface GeneratedChunkListener {
    void completed(ResourceId dimension, ChunkPos position, PregenBridge.ChunkResult result);
}
