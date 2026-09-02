package io.github.playroomproject.mappreview.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.client.render.*;
import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.core.filter.PreviewFilter;
import io.github.playroomproject.mappreview.core.tile.*;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.WorldgenSampler;
import io.github.playroomproject.mappreview.testing.SyntheticWorldgen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RenderingTest {
    @Test void retiredAndRejectedDependentStagesAreNotDisplayFailures() {
        for (var expected : List.of(new java.util.concurrent.CancellationException("Obsolete viewport"),
                new java.util.concurrent.RejectedExecutionException("Tile queue full"))) {
            var source = new CompletableFuture<Integer>();
            var received = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            source.minimalCompletionStage().thenApply(value -> value + 1).whenComplete((value, error) -> received.set(error));
            source.completeExceptionally(expected);
            assertInstanceOf(java.util.concurrent.CompletionException.class, received.get());
            assertTrue(TileFailures.reportable(received.get()).isEmpty());
        }
    }

    @Test void dependentStagePreservesAnActualGeneratorFailure() {
        var failure = new IllegalStateException("Generator codec failed");
        var received = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        CompletableFuture.<Integer>failedFuture(failure).minimalCompletionStage().thenApply(value -> value + 1)
                .whenComplete((value, error) -> received.set(error));
        assertSame(failure, TileFailures.reportable(received.get()).orElseThrow());
    }

    @Test void recoloringChangesPixelsWithoutCallingWorldgenAgain() {
        AtomicInteger samples = new AtomicInteger();
        var context = SyntheticWorldgen.context(1);
        var sampler = new WorldgenSampler() {
            @Override public int biome(int x, int y, int z) { samples.incrementAndGet(); return 0; }
        };
        var raw = (RasterTile) sampler.sample(key(0, DataLayer.BIOMES), context, CancellationToken.NONE);
        var a = TileColorizer.colorize(raw, null, new BiomeColors(context.biomes(), Map.of(), Map.of()), TileColorizer.Style.BIOMES, 63, PreviewFilter.IDENTITY);
        var b = TileColorizer.colorize(raw, null, new BiomeColors(context.biomes(), Map.of(context.biomes().biome(0).id(), 0xffff0011), Map.of()), TileColorizer.Style.BIOMES, 63, PreviewFilter.IDENTITY);
        assertNotEquals(a.pixel(0), b.pixel(0));
        assertEquals(64, samples.get());
    }

    @Test void missingFilterDependenciesFailInsteadOfGuessingHeights() {
        var raw = tile(0, DataLayer.BIOMES);
        PreviewFilter needsHeight = new PreviewFilter() { @Override public boolean needsHeights() { return true; } };
        assertThrows(IllegalArgumentException.class, () -> TileColorizer.colorize(raw, null,
                new BiomeColors(SyntheticWorldgen.context(1).biomes(), Map.of(), Map.of()), TileColorizer.Style.BIOMES, 63, needsHeight));
    }

    @Test void layersFromDifferentGridsCannotBeCombined() {
        assertThrows(IllegalArgumentException.class, () -> TileColorizer.colorize(tile(0, DataLayer.BIOMES), tile(1, DataLayer.HEIGHT),
                new BiomeColors(SyntheticWorldgen.context(1).biomes(), Map.of(), Map.of()), TileColorizer.Style.BIOMES, 63, PreviewFilter.IDENTITY));
    }

    @Test void displayRevisionsAndEpochsRejectStaleUploads() {
        var colored = TileColorizer.raw(tile(0, DataLayer.BIOMES), ignored -> 0xff123456);
        var queue = new RenderUploadQueue(2, colored.byteSize() * 2);
        var renderer = new RecordingRenderer();
        queue.activate(1, 1);
        assertTrue(queue.offer(1, 1, colored));
        queue.activate(2, 1);
        assertFalse(queue.offer(1, 1, colored));
        assertEquals(0, queue.drain(renderer, 10));
        queue.activate(2, 2);
        assertFalse(queue.offer(2, 1, colored));
        assertTrue(queue.offer(2, 2, colored));
        assertEquals(1, queue.drain(renderer, 1));
        assertEquals(List.of(colored), renderer.uploads);
    }

    @Test void uploadQueueHasHardByteAndTileBounds() {
        var colored = TileColorizer.raw(tile(0, DataLayer.BIOMES), ignored -> 0xff000000);
        var queue = new RenderUploadQueue(2, colored.byteSize());
        queue.activate(1, 1);
        assertTrue(queue.offer(1, 1, colored));
        assertFalse(queue.offer(1, 1, colored));
        assertEquals(colored.byteSize(), queue.bytes());
        assertEquals(1, queue.drain(new RecordingRenderer(), 1));
        assertEquals(0, queue.bytes());
    }

    @Test void workersCannotDrainOrActivateGraphicsResources() {
        var queue = new RenderUploadQueue(2, 4096);
        CompletableFuture.runAsync(() -> {
            assertThrows(IllegalStateException.class, () -> queue.drain(new RecordingRenderer(), 1));
            assertThrows(IllegalStateException.class, () -> queue.activate(1, 1));
        }).join();
    }

    @Test void flatHeightMeshHasCorrectExtentAndUpwardWinding() {
        var context = SyntheticWorldgen.context(1);
        var sampler = new WorldgenSampler() { @Override public int height(int x, int z, HeightMode mode) { return 80; } };
        var height = (RasterTile) sampler.sample(key(-1, DataLayer.HEIGHT), context, CancellationToken.NONE);
        var mesh = TerrainMesh.fromHeightTile(height);
        assertEquals(81, mesh.vertexCount());
        assertEquals(128, mesh.triangleCount());
        assertEquals(-256, mesh.originX());
        var vertices = mesh.vertices();
        assertEquals(0, vertices.get(0));
        assertEquals(80, vertices.get(1));
        assertEquals(256, vertices.get(vertices.limit() - 1));
        var indices = mesh.indices();
        int a = indices.get(0) * 3;
        int b = indices.get(1) * 3;
        int c = indices.get(2) * 3;
        double upward = (vertices.get(b + 2) - vertices.get(a + 2)) * (vertices.get(c) - vertices.get(a))
                - (vertices.get(b) - vertices.get(a)) * (vertices.get(c + 2) - vertices.get(a + 2));
        assertTrue(upward > 0);
        assertTrue(vertices.isReadOnly());
        assertTrue(indices.isReadOnly());
    }

    @Test void adjacentMeshEdgesReuseIdenticalWorldHeights() {
        var left = TerrainMesh.fromHeightTile(tile(-1, DataLayer.HEIGHT));
        var right = TerrainMesh.fromHeightTile(tile(0, DataLayer.HEIGHT));
        for (int row = 0; row < 9; row++) {
            assertEquals(left.vertices().get((row * 9 + 8) * 3 + 1), right.vertices().get(row * 9 * 3 + 1));
        }
    }

    private static TileKey key(int x, DataLayer layer) { return new TileKey(SyntheticWorldgen.context(1).fingerprint(), TileRequest.of(x, 0, 32, layer, 64)); }
    private static RasterTile tile(int x, DataLayer layer) {
        var context = SyntheticWorldgen.context(1);
        return (RasterTile) SyntheticWorldgen.sampler(context.seed(), context.biomes().size()).sample(key(x, layer), context, CancellationToken.NONE);
    }
    private static final class RecordingRenderer implements PreviewRenderer {
        private final List<ColoredTile> uploads = new ArrayList<>();
        @Override public void upload(ColoredTile tile) { uploads.add(tile); }
        @Override public void draw(PreviewCamera camera) { }
        @Override public void release(TileKey key) { }
        @Override public void close() { }
    }
}
