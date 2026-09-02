package io.github.playroomproject.mappreview.testing;

import io.github.playroomproject.mappreview.core.api.BiomePalette;
import io.github.playroomproject.mappreview.core.api.PreviewBiome;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.PreviewDimension;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.api.WorldgenEnvironment;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.worldgen.BackendCapabilities;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.LayerSupport;
import io.github.playroomproject.mappreview.core.worldgen.SupportLevel;
import io.github.playroomproject.mappreview.core.worldgen.WorldgenSampler;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic test data. This is deliberately outside the production runtime and is not Minecraft worldgen. */
public final class SyntheticWorldgen {
    private SyntheticWorldgen() { }

    public static PreviewContext context(long seed) {
        var environment = new WorldgenEnvironment("synthetic-test", "jvm", "17", new ResourceId("fixture:default"),
                "synthetic", Fingerprint.builder().add("fixture-v1").finish(), List.of("base:sha256:fixture"), Map.of(), Map.of());
        var biomes = new BiomePalette(List.of(
                new PreviewBiome(new ResourceId("fixture:plains"), Set.of(new ResourceId("fixture:land")), 0xff7fbf55),
                new PreviewBiome(new ResourceId("fixture:forest"), Set.of(new ResourceId("fixture:land")), 0xff447744),
                new PreviewBiome(new ResourceId("fixture:ocean"), Set.of(new ResourceId("fixture:water")), 0xff336699)));
        return new PreviewContext(environment, seed, new PreviewDimension(new ResourceId("fixture:dimension"), -64, 1024, 63), biomes);
    }

    public static BackendFactory factory(int parallelism) {
        return new BackendFactory() {
            @Override public ResourceId id() { return new ResourceId("fixture:synthetic"); }
            @Override public int maximumConcurrency() { return parallelism; }
            @Override public BackendCapabilities capabilities(PreviewContext context) { return SyntheticWorldgen.capabilities(); }
            @Override public WorldgenSampler open(PreviewContext context) { return sampler(context.seed(), context.biomes().size()); }
        };
    }

    public static BackendCapabilities capabilities() {
        var sampled = new LayerSupport(SupportLevel.SAMPLED, "Synthetic fixture data only");
        return new BackendCapabilities(Map.of(DataLayer.BIOMES, sampled, DataLayer.HEIGHT, sampled, DataLayer.CAVE_BIOMES, sampled));
    }

    public static WorldgenSampler sampler(long seed, int paletteSize) {
        return new WorldgenSampler() {
            @Override public int biome(int x, int y, int z) { return Math.floorMod(mix(seed, x, y, z), paletteSize); }
            @Override public int height(int x, int z, HeightMode mode) {
                return 64 + (int) Math.floorMod(mix(seed, Math.floorDiv(x, 8), 0, Math.floorDiv(z, 8)), 192L);
            }
        };
    }

    private static long mix(long seed, int x, int y, int z) {
        long value = seed ^ x * 0x9e3779b97f4a7c15L ^ z * 0xc2b2ae3d27d4eb4fL ^ y;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        return value ^ value >>> 31;
    }
}
