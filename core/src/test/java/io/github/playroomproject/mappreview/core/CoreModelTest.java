package io.github.playroomproject.mappreview.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.core.api.*;
import io.github.playroomproject.mappreview.core.cache.*;
import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.core.scheduler.*;
import io.github.playroomproject.mappreview.core.tile.*;
import io.github.playroomproject.mappreview.core.worldgen.*;
import io.github.playroomproject.mappreview.testing.SyntheticWorldgen;
import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoreModelTest {
    @ParameterizedTest @ValueSource(strings = {"plains", "Minecraft:plains", "a:b:c", "a:", ":b", "a:hello world"})
    void rejectsUnstableRegistryIdentifiers(String id) { assertThrows(IllegalArgumentException.class, () -> new ResourceId(id)); }

    @Test void fingerprintHasUnambiguousFieldBoundaries() {
        assertNotEquals(Fingerprint.builder().add("ab").add("c").finish(), Fingerprint.builder().add("a").add("bc").finish());
    }

    @Test void canonicalMapsIgnoreInsertionOrder() {
        var a = new LinkedHashMap<String, String>();
        a.put("z", "1"); a.put("a", "2");
        var b = new LinkedHashMap<String, String>();
        b.put("a", "2"); b.put("z", "1");
        assertEquals(Fingerprint.builder().addSorted(a).finish(), Fingerprint.builder().addSorted(b).finish());
    }

    @Test void everyWorldgenIdentityFieldInvalidatesTheContext() {
        var base = SyntheticWorldgen.context(1);
        var e = base.environment();
        var variants = List.of(
                new WorldgenEnvironment("other", e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), "other", e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), "other", e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), new ResourceId("fixture:other"), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), "other", e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), Fingerprint.builder().add("other").finish(), e.orderedDatapackDigests(), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), List.of("changed-pack-content"), e.worldgenMods(), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), Map.of("a-mod", "version-and-content-digest"), e.worldgenConfigDigests()),
                new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), e.orderedDatapackDigests(), e.worldgenMods(), Map.of("tectonic", "changed-settings-digest")));
        variants.forEach(v -> assertNotEquals(base.fingerprint(), new PreviewContext(v, base.seed(), base.dimension(), base.biomes()).fingerprint()));
        assertNotEquals(base.fingerprint(), SyntheticWorldgen.context(2).fingerprint());
        assertNotEquals(base.fingerprint(), new PreviewContext(e, 1, new PreviewDimension(new ResourceId("fixture:other"), -64, 1024, 63), base.biomes()).fingerprint());
    }

    @Test void datapackOrderIsSemanticallySignificant() {
        var e = SyntheticWorldgen.context(1).environment();
        var a = new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), List.of("a", "b"), Map.of(), Map.of());
        var b = new WorldgenEnvironment(e.minecraftVersion(), e.loaderId(), e.loaderVersion(), e.worldPreset(), e.generatorIdentity(), e.generatorSettings(), List.of("b", "a"), Map.of(), Map.of());
        assertNotEquals(a.fingerprint(), b.fingerprint());
    }

    @Test void paletteIsStableAndDetachedFromRegistryOrder() {
        var entries = new ArrayList<>(SyntheticWorldgen.context(1).biomes().entries());
        var a = new BiomePalette(entries);
        Collections.reverse(entries);
        var b = new BiomePalette(entries);
        entries.clear();
        assertEquals(a.fingerprint(), b.fingerprint());
        assertEquals(a.entries(), b.entries());
        assertThrows(IllegalArgumentException.class, () -> new BiomePalette(List.of(a.biome(0), a.biome(0))));
    }

    @Test void keysSeparateChannelsYHeightModeResolutionAndQueries() {
        var base = TileRequest.of(-1, -2, 4, DataLayer.BIOMES, 64);
        assertNotEquals(base, TileRequest.of(-1, -2, 4, DataLayer.BIOMES, 65));
        assertNotEquals(base, TileRequest.of(-1, -2, 8, DataLayer.BIOMES, 64));
        assertNotEquals(base, TileRequest.of(-1, -2, 4, DataLayer.CAVE_BIOMES, 64));
        assertEquals(TileRequest.of(0, 0, 4, DataLayer.HEIGHT, 0), TileRequest.of(0, 0, 4, DataLayer.HEIGHT, 120));
        var a = new TileRequest(0, 0, 256, 4, DataLayer.HEIGHT, 0, HeightMode.OCEAN_FLOOR, StructureQuery.DEFAULT);
        assertNotEquals(a, TileRequest.of(0, 0, 4, DataLayer.HEIGHT, 0));
    }

    @Test void negativeCoordinatesAndHeightHaloCoverSharedEdges() {
        var left = TileRequest.of(-1, -1, 4, DataLayer.HEIGHT, 0);
        var right = TileRequest.of(0, -1, 4, DataLayer.HEIGHT, 0);
        assertEquals(-256, left.originX());
        assertEquals(-260, left.sampleX(0));
        assertEquals(right.sampleX(1), left.sampleX(left.cells() + 1));
        assertThrows(IllegalArgumentException.class, () -> TileRequest.of(Integer.MAX_VALUE, 0, 1, DataLayer.BIOMES, 0));
    }

    @Test void rawTileOwnershipCannotBeMutatedAfterPublication() {
        var builder = RasterTile.builder(key(0));
        builder.set(0, 7);
        var tile = builder.freeze();
        assertEquals(7, tile.value(0));
        assertThrows(IllegalStateException.class, () -> builder.set(0, 3));
        assertThrows(IllegalStateException.class, builder::freeze);
        assertThrows(ReadOnlyBufferException.class, () -> tile.values().put(0, 3));
    }

    @Test void lruRespectsByteBudgetAndOversizeAdmission() {
        var a = RasterTile.builder(key(0)).freeze();
        var b = RasterTile.builder(key(1)).freeze();
        var c = RasterTile.builder(key(2)).freeze();
        var cache = new TileCache(a.byteSize() * 2);
        assertTrue(cache.put(a)); assertTrue(cache.put(b));
        assertSame(a, cache.get(a.key()));
        assertTrue(cache.put(c));
        assertNull(cache.get(b.key()));
        assertSame(a, cache.get(a.key()));
        assertTrue(cache.stats().bytes() <= cache.stats().maximumBytes());
        var oversized = RasterTile.builder(new TileKey(key(0).sessionFingerprint(), TileRequest.of(0, 0, 1, DataLayer.BIOMES, 64))).freeze();
        assertFalse(cache.put(oversized));
        assertSame(c, cache.get(c.key()));
        cache.resize(0);
        assertEquals(0, cache.stats().entries());
    }

    @Test void compressedDataRoundTripsAndRejectsCorruptionOrWrongIdentity() throws IOException {
        var builder = RasterTile.builder(key(0));
        for (int i = 0; i < key(0).request().samplesPerSide() * key(0).request().samplesPerSide(); i++) { builder.set(i, i * 113); }
        var original = builder.freeze();
        byte[] encoded = RasterTileCodec.encode(original);
        var decoded = RasterTileCodec.decode(original.key(), encoded);
        assertEquals(original.values(), decoded.values());
        assertThrows(IOException.class, () -> RasterTileCodec.decode(key(1), encoded));
        encoded[encoded.length - 5] ^= 0x40;
        assertThrows(IOException.class, () -> RasterTileCodec.decode(original.key(), encoded));
    }

    @Test void biomeViewNeverSamplesHeightsColumnsOrStructures() {
        AtomicInteger biomes = new AtomicInteger();
        var sampler = new WorldgenSampler() { @Override public int biome(int x, int y, int z) { biomes.incrementAndGet(); return 0; } };
        var tile = sampler.sample(key(0), SyntheticWorldgen.context(1), CancellationToken.NONE);
        assertInstanceOf(RasterTile.class, tile);
        assertEquals(64, biomes.get());
    }

    @Test void cancellationIsCheckedInsideLongRasterLoops() {
        AtomicInteger calls = new AtomicInteger();
        var sampler = new WorldgenSampler() { @Override public int biome(int x, int y, int z) { calls.incrementAndGet(); return 0; } };
        var dense = new TileKey(key(0).sessionFingerprint(), TileRequest.of(0, 0, 1, DataLayer.BIOMES, 64));
        assertThrows(java.util.concurrent.CancellationException.class, () -> sampler.sample(dense, SyntheticWorldgen.context(1), () -> calls.get() >= 33));
        assertTrue(calls.get() <= 64);
    }

    @Test void tallDimensionsDoNotOverflowHeights() {
        var context = SyntheticWorldgen.context(1);
        var sampler = new WorldgenSampler() { @Override public int height(int x, int z, HeightMode mode) { return 70_000; } };
        var tile = (RasterTile) sampler.sample(new TileKey(context.fingerprint(), TileRequest.of(0, 0, 32, DataLayer.HEIGHT, 0)), context, CancellationToken.NONE);
        assertEquals(70_000, tile.value(0));
    }

    @Test void candidateCannotBeStoredAsVerifiedStructure() {
        var key = new TileKey(key(0).sessionFingerprint(), TileRequest.of(0, 0, 32, DataLayer.VERIFIED_STRUCTURES, 0));
        assertThrows(IllegalArgumentException.class, () -> new StructureTile(key,
                List.of(new PreviewStructure(new ResourceId("fixture:ruin"), 0, 0, 0, SupportLevel.ESTIMATED)), false));
    }

    @Test void cameraZoomKeepsThePointerAnchored() {
        var before = new PreviewCamera(-432.25, 987.5, 4, 800, 600);
        var after = before.zoomAt(123, 456, 0.5);
        assertEquals(before.worldX(123), after.worldX(123), 1e-9);
        assertEquals(before.worldZ(456), after.worldZ(456), 1e-9);
    }

    @Test void farZoomUsesLargerTilesAndOnlyCoarsePrefetch() {
        var planner = new ViewportPlanner(256, List.of(32, 16, 8, 4, 1), 1, 4096);
        var plan = planner.plan(new PreviewCamera(0, 0, 1024, 1280, 720), DataLayer.BIOMES, 64, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT);
        assertTrue(plan.size() < 4096);
        assertTrue(plan.get(0).request().tileSize() > 256);
        for (int i = 1; i < plan.size(); i++) { assertTrue(plan.get(i - 1).priority().compareTo(plan.get(i).priority()) <= 0); }
        assertTrue(plan.stream().filter(p -> p.priority().prefetch()).allMatch(p -> p.priority().refinementPass() == 0));
    }

    @Test void colorsPreferOverrideThenTagsThenEnvironmentThenStableFallback() {
        var biome = new PreviewBiome(new ResourceId("other:unknown"), Set.of(new ResourceId("fixture:land")), 0xff123456);
        var palette = new BiomePalette(List.of(biome));
        assertEquals(0xffabcdef, new BiomeColors(palette, Map.of(biome.id(), 0xffabcdef), Map.of(new ResourceId("fixture:land"), 0xff000011)).argb(0));
        assertEquals(0xff000011, new BiomeColors(palette, Map.of(), Map.of(new ResourceId("fixture:land"), 0xff000011)).argb(0));
        assertEquals(0xff123456, new BiomeColors(palette, Map.of(), Map.of()).argb(0));
        assertEquals(0xff7fbf55, BiomeColors.parseHex("#7FBF55"));
    }

    @Test void providersHaveStablePriorityAndFreezeAfterBootstrap() {
        record Provider(ResourceId id, int priority) implements PrioritizedProvider { }
        var registry = new ProviderRegistry<Provider>();
        registry.register(new Provider(new ResourceId("fixture:z"), 1));
        registry.register(new Provider(new ResourceId("fixture:a"), 1));
        registry.register(new Provider(new ResourceId("fixture:fast"), 2));
        assertEquals("fixture:fast", registry.providers().get(0).id().value());
        assertEquals("fixture:a", registry.providers().get(1).id().value());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Provider(new ResourceId("fixture:a"), 9)));
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(new Provider(new ResourceId("fixture:new"), 0)));
    }

    private static TileKey key(int x) { return new TileKey(SyntheticWorldgen.context(1).fingerprint(), TileRequest.of(x, 0, 32, DataLayer.BIOMES, 64)); }
}
