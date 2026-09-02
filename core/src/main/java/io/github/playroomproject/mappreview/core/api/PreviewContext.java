package io.github.playroomproject.mappreview.core.api;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.Objects;

/** Immutable session input. Replacing any world generation input creates a new context. */
public final class PreviewContext {
    private final WorldgenEnvironment environment;
    private final long seed;
    private final PreviewDimension dimension;
    private final BiomePalette biomes;
    private final Fingerprint fingerprint;

    public PreviewContext(WorldgenEnvironment environment, long seed, PreviewDimension dimension, BiomePalette biomes) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.seed = seed;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.biomes = Objects.requireNonNull(biomes, "biomes");
        fingerprint = Fingerprint.builder().add("context").add(MapPreView.CACHE_FORMAT_VERSION)
                .add(environment.fingerprint().hex()).add(seed).add(dimension.id().value())
                .add(dimension.minY()).add(dimension.maxYExclusive()).add(dimension.seaLevel())
                .add(biomes.fingerprint().hex()).finish();
    }

    public WorldgenEnvironment environment() { return environment; }
    public long seed() { return seed; }
    public PreviewDimension dimension() { return dimension; }
    public BiomePalette biomes() { return biomes; }
    public Fingerprint fingerprint() { return fingerprint; }
}
