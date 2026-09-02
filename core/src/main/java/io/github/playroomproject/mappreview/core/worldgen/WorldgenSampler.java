package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.tile.StructureTile;
import io.github.playroomproject.mappreview.core.tile.TileData;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import java.util.function.IntConsumer;

/**
 * Samples the active generator, never a reimplementation of vanilla generation in the core.
 * Coordinates are block coordinates; mapping to quart/chunk units belongs in the version bridge.
 * Block-state IDs must use a stable palette included in the generator settings fingerprint.
 */
public interface WorldgenSampler extends AutoCloseable {
    default int biome(int x, int y, int z) { throw unavailable(); }
    default int height(int x, int z, HeightMode mode) { throw unavailable(); }
    default int surface(int x, int z) { throw unavailable(); }
    default float caveDensity(int x, int y, int z) { throw unavailable(); }
    default int accurateCaveBlock(int x, int y, int z) { throw unavailable(); }
    default boolean slimeChunk(int chunkX, int chunkZ) { throw unavailable(); }
    default void column(int x, int z, int minY, int maxYExclusive, IntConsumer states, CancellationToken token) {
        throw unavailable();
    }
    default StructureTile structures(TileKey key, CancellationToken token) { throw unavailable(); }

    /** Fast adapters may override with equivalent batched sampling and cooperative cancellation. */
    default TileData sample(TileKey key, PreviewContext context, CancellationToken token) {
        return TileSampling.sample(this, key, context, token);
    }

    @Override default void close() { }

    private static UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException("Map PreView backend does not implement this capability");
    }
}
