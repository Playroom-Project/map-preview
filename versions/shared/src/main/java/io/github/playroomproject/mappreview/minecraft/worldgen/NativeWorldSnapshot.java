package io.github.playroomproject.mappreview.minecraft.worldgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
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
import io.github.playroomproject.mappreview.core.worldgen.LayerSupport;
import io.github.playroomproject.mappreview.core.worldgen.SupportLevel;
import io.github.playroomproject.mappreview.core.worldgen.WorldgenSampler;
import io.github.playroomproject.mappreview.minecraft.WorldCreationInput;
import io.github.playroomproject.mappreview.minecraft.WorldCreationSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

/** Frozen registry references and serialized generators owned by one editor session. */
public final class NativeWorldSnapshot implements WorldCreationSnapshot {
    private final WorldCreationInput input;
    private final List<PreviewContext> dimensions;
    private final Map<ResourceId, BackendFactory> factories;
    private final AtomicBoolean closed = new AtomicBoolean();

    public NativeWorldSnapshot(RegistryWrapper.WrapperLookup registries,
            Map<RegistryKey<DimensionOptions>, DimensionOptions> selectedDimensions,
            long seed, String loaderId, String loaderVersion, List<String> packs,
            Map<String, String> mods, Fingerprint snapshotIdentity) {
        this(registries, selectedDimensions, seed, loaderId, loaderVersion, packs, mods, snapshotIdentity, true);
    }

    public NativeWorldSnapshot(RegistryWrapper.WrapperLookup registries,
            Map<RegistryKey<DimensionOptions>, DimensionOptions> selectedDimensions,
            long seed, String loaderId, String loaderVersion, List<String> packs,
            Map<String, String> mods, Fingerprint snapshotIdentity, boolean generateStructures) {
        input = new WorldCreationInput(Long.toString(seed), new ResourceId("map_preview:selected"), Map.of());
        var nativeBiomes = registries.getWrapperOrThrow(RegistryKeys.BIOME);
        var entries = new ArrayList<PreviewBiome>();
        nativeBiomes.streamEntries().forEach(entry -> {
            var tags = entry.streamTags().map(tag -> new ResourceId(tag.id().toString())).collect(Collectors.toSet());
            var id = new ResourceId(entry.registryKey().getValue().toString());
            int tint = entry.value().getGrassColorAt(0, 0);
            if (tags.contains(new ResourceId("minecraft:is_ocean")) || tags.contains(new ResourceId("minecraft:is_river"))) {
                tint = entry.value().getWaterColor();
            }
            entries.add(new PreviewBiome(id, tags, tint == 0 ? 0 : 0xff000000 | tint));
        });
        var palette = new BiomePalette(entries);
        var biomeIds = new IdentityHashMap<Biome, Integer>();
        nativeBiomes.streamEntries().forEach(entry -> biomeIds.put(entry.value(),
                palette.localId(new ResourceId(entry.registryKey().getValue().toString()))));
        var contexts = new ArrayList<PreviewContext>();
        var backends = new LinkedHashMap<ResourceId, BackendFactory>();
        var ops = RegistryOps.of(JsonOps.INSTANCE, registries);
        var registryFingerprint = NativeRegistryFingerprint.capture(registries);
        selectedDimensions.entrySet().stream().sorted(Map.Entry.comparingByKey(
                java.util.Comparator.comparing(key -> key.getValue().toString()))).forEach(entry -> {
            var options = entry.getValue();
            var generator = options.chunkGenerator();
            JsonElement encoded = ChunkGenerator.CODEC.encodeStart(ops, generator).result()
                    .orElseThrow(() -> new IllegalArgumentException("The selected generator cannot be serialized for a detached preview"));
            var type = options.dimensionTypeEntry().value();
            var id = new ResourceId(entry.getKey().getValue().toString());
            var dimension = new PreviewDimension(id, type.minY(), type.minY() + type.height(), generator.getSeaLevel());
            var settings = Fingerprint.builder().add(snapshotIdentity.hex()).add(registryFingerprint.hex()).add(encoded.toString())
                    .add(type.minY()).add(type.height()).finish();
            var environment = new WorldgenEnvironment(SharedConstants.getGameVersion().getName(), loaderId,
                    loaderVersion, input.preset(), generator.getClass().getName(), settings, packs, mods,
                    Map.of("snapshot", snapshotIdentity.hex(), "generate_structures", Boolean.toString(generateStructures)));
            var context = new PreviewContext(environment, seed, dimension, palette);
            contexts.add(context);
            backends.put(id, new BackendFactory() {
                @Override public ResourceId id() { return new ResourceId("map_preview:native"); }
                @Override public int dataVersion() { return 1; }
                @Override public int maximumConcurrency() {
                    Class<?> source = generator.getBiomeSource().getClass();
                    boolean nativeSource = source == net.minecraft.world.biome.source.MultiNoiseBiomeSource.class
                            || source == net.minecraft.world.biome.source.TheEndBiomeSource.class
                            || source == net.minecraft.world.biome.source.FixedBiomeSource.class;
                    return nativeSource && (generator.getClass() == NoiseChunkGenerator.class
                            || generator.getClass() == FlatChunkGenerator.class) ? 4 : 1;
                }
                @Override public BackendCapabilities capabilities(PreviewContext ignored) {
                    var support = new EnumMap<DataLayer, LayerSupport>(DataLayer.class);
                    var sampled = new LayerSupport(SupportLevel.SAMPLED, "Native generator sample; terrain decoration is not included");
                    support.put(DataLayer.BIOMES, sampled);
                    support.put(DataLayer.CAVE_BIOMES, sampled);
                    support.put(DataLayer.SLIME_CHUNKS, new LayerSupport(SupportLevel.VERIFIED, "Vanilla slime-chunk seed rule"));
                    if (generateStructures) {
                        support.put(DataLayer.STRUCTURE_CANDIDATES, new LayerSupport(SupportLevel.ESTIMATED,
                                "Placement candidates; terrain suitability and final structure starts are not verified"));
                    }
                    if (generator instanceof NoiseChunkGenerator || generator instanceof FlatChunkGenerator) {
                        support.put(DataLayer.HEIGHT, sampled);
                        support.put(DataLayer.SURFACE, sampled);
                    }
                    if (generator instanceof NoiseChunkGenerator) {
                        support.put(DataLayer.CAVE_DENSITY, new LayerSupport(SupportLevel.ESTIMATED,
                                "Raw density only; aquifers, carvers and placed features are not included"));
                    }
                    return new BackendCapabilities(support);
                }
                @Override public WorldgenSampler open(PreviewContext requested) {
                    if (closed.get()) { throw new IllegalStateException("Map PreView snapshot has closed"); }
                    if (requested != context) { throw new IllegalArgumentException("The context belongs to another snapshot"); }
                    ChunkGenerator detached = ChunkGenerator.CODEC.parse(ops, encoded).result()
                            .orElseThrow(() -> new IllegalArgumentException("The selected generator cannot be reconstructed for a detached preview"));
                    return new NativeWorldgenSampler(registries, detached, requested, biomeIds);
                }
            });
        });
        if (contexts.isEmpty()) { throw new IllegalArgumentException("The selected world has no dimensions"); }
        dimensions = List.copyOf(contexts);
        factories = Map.copyOf(backends);
    }

    @Override public WorldCreationInput input() { return input; }
    @Override public List<PreviewContext> dimensions() { return dimensions; }
    @Override public BackendFactory backend(ResourceId dimension) {
        var factory = factories.get(dimension);
        if (factory == null) { throw new IllegalArgumentException("Unknown preview dimension: " + dimension); }
        return factory;
    }
    /** Registry storage belongs to vanilla; samplers retain immutable references until they drain. */
    @Override public void close() { closed.set(true); }
}
