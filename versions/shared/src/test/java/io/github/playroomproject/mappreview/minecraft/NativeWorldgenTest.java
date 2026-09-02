package io.github.playroomproject.mappreview.minecraft;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.StructureQuery;
import io.github.playroomproject.mappreview.core.worldgen.SupportLevel;
import io.github.playroomproject.mappreview.minecraft.mixin.CreateWorldScreenInvoker;
import io.github.playroomproject.mappreview.minecraft.worldgen.NativeRegistryFingerprint;
import io.github.playroomproject.mappreview.minecraft.worldgen.NativeWorldSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import io.github.playroomproject.mappreview.minecraft.worldgen.NativeDimensions;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Native registry and generator tests run through Fabric Loader's JUnit class loader. */
@Timeout(120)
class NativeWorldgenTest {
    private static RegistryWrapper.WrapperLookup registries;
    private static net.minecraft.resource.LifecycledResourceManager resources;
    private static Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions;
    private static final Fingerprint EDITOR = Fingerprint.builder().add("native-test-editor").finish();
    private static final int[][] POSITIONS = {{0, 0}, {-1, -1}, {-17, 31}, {255, -257}, {1024, -2048}};

    @BeforeAll static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        var packs = NativeTestResources.packs();
        packs.scanPacks();
        packs.setEnabledProfiles(List.of("vanilla"));
        resources = new net.minecraft.resource.LifecycledResourceManagerImpl(net.minecraft.resource.ResourceType.SERVER_DATA, packs.createResourcePacks());
        var base = net.minecraft.registry.ServerDynamicRegistryType.createCombinedDynamicRegistries();
        var loaded = NativeTestResources.load(resources, base.getCombinedRegistryManager());
        var combined = base.with(net.minecraft.registry.ServerDynamicRegistryType.WORLDGEN, loaded).getCombinedRegistryManager();
        var tags = new net.minecraft.registry.tag.TagManagerLoader(combined);
        tags.reload(new net.minecraft.resource.ResourceReloader.Synchronizer() {
            @Override public <T> java.util.concurrent.CompletableFuture<T> whenPrepared(T prepared) {
                return java.util.concurrent.CompletableFuture.completedFuture(prepared);
            }
        }, resources, net.minecraft.util.profiler.DummyProfiler.INSTANCE, net.minecraft.util.profiler.DummyProfiler.INSTANCE,
                Runnable::run, Runnable::run).join();
        tags.getRegistryTags().forEach(loadedTags -> bindTags(combined, loadedTags));
        registries = combined;
        dimensions = dimensions(WorldPresets.DEFAULT);
    }

    @AfterAll static void closeResources() { if (resources != null) { resources.close(); } }

    private static <T> void bindTags(net.minecraft.registry.DynamicRegistryManager manager,
                                    net.minecraft.registry.tag.TagManagerLoader.RegistryTags<T> loaded) {
        manager.get(loaded.key()).populateTags(loaded.tags().entrySet().stream().collect(Collectors.toMap(
                entry -> net.minecraft.registry.tag.TagKey.of(loaded.key(), entry.getKey()), entry -> List.copyOf(entry.getValue()))));
    }

    private static Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions(RegistryKey<WorldPreset> preset) {
        return NativeDimensions.copyOf(registries.getWrapperOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(preset).value()
                .createDimensionsRegistryHolder().dimensions());
    }
    private static NativeWorldSnapshot snapshot(long seed) { return snapshot(seed, dimensions); }
    private static NativeWorldSnapshot snapshot(long seed, Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions) {
        return new NativeWorldSnapshot(registries, dimensions, seed, "fabric", "0.16.14", List.of("vanilla"), Map.of(), EDITOR);
    }

    @Test void discoversVanillaAndAdditionalDimensions() {
        var extended = new HashMap<>(dimensions);
        extended.put(RegistryKey.of(RegistryKeys.DIMENSION, new Identifier("example:extra")), dimensions.get(DimensionOptions.NETHER));
        try (var snapshot = snapshot(1, extended)) {
            assertEquals(4, snapshot.dimensions().size());
            assertTrue(snapshot.dimensions().stream().anyMatch(context -> context.dimension().id().value().equals("example:extra")));
        }
    }

    @Test void biomeCoordinatesMatchNativeQuartSamplingForAllDimensions() {
        for (long seed : new long[]{0, -8192, 123456789}) {
            try (var snapshot = snapshot(seed)) {
                for (var context : snapshot.dimensions()) {
                    var nativeOptions = dimensions.entrySet().stream().filter(entry -> entry.getKey().getValue().toString()
                            .equals(context.dimension().id().value())).findFirst().orElseThrow().getValue();
                    var generator = (NoiseChunkGenerator) nativeOptions.chunkGenerator();
                    var noise = NoiseConfig.create(generator.getSettings().value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
                    try (var sampler = snapshot.backend(context.dimension().id()).open(context)) {
                        for (int[] position : POSITIONS) {
                            for (int y : new int[]{context.dimension().minY(), 32, 100}) {
                                var expected = generator.getBiomeSource().getBiome(Math.floorDiv(position[0], 4), Math.floorDiv(y, 4),
                                        Math.floorDiv(position[1], 4), noise.getMultiNoiseSampler()).getKey().orElseThrow().getValue().toString();
                                assertEquals(expected, context.biomes().biome(sampler.biome(position[0], y, position[1])).id().value());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void rawHeightsMatchNativeGeneratorIncludingNegativeCoordinates() {
        try (var snapshot = snapshot(-123456789)) {
            for (var context : snapshot.dimensions()) {
                var options = dimensions.entrySet().stream().filter(entry -> entry.getKey().getValue().toString()
                        .equals(context.dimension().id().value())).findFirst().orElseThrow().getValue();
                var generator = (NoiseChunkGenerator) options.chunkGenerator();
                var noise = NoiseConfig.create(generator.getSettings().value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), context.seed());
                var height = HeightLimitView.create(context.dimension().minY(), context.dimension().maxYExclusive() - context.dimension().minY());
                try (var sampler = snapshot.backend(context.dimension().id()).open(context)) {
                    for (int[] position : POSITIONS) {
                        assertEquals(generator.getHeight(position[0], position[1], Heightmap.Type.WORLD_SURFACE_WG, height, noise),
                                sampler.height(position[0], position[1], HeightMode.WORLD_SURFACE));
                        assertEquals(generator.getHeight(position[0], position[1], Heightmap.Type.OCEAN_FLOOR_WG, height, noise),
                                sampler.height(position[0], position[1], HeightMode.OCEAN_FLOOR));
                    }
                }
            }
        }
    }

    @Test void fourWorkerOwnedGeneratorsProduceIdenticalSamples() throws Exception {
        try (var snapshot = snapshot(68439)) {
            var context = snapshot.dimensions().stream().filter(value -> value.dimension().id().value().equals("minecraft:overworld")).findFirst().orElseThrow();
            var factory = snapshot.backend(context.dimension().id());
            assertEquals(4, factory.maximumConcurrency());
            var executor = Executors.newFixedThreadPool(4);
            try {
                Callable<List<Integer>> sample = () -> {
                    try (var sampler = factory.open(context)) {
                        var values = new ArrayList<Integer>();
                        for (int[] position : POSITIONS) {
                            values.add(sampler.biome(position[0], 64, position[1]));
                            values.add(sampler.height(position[0], position[1], HeightMode.WORLD_SURFACE));
                        }
                        return values;
                    }
                };
                var results = executor.invokeAll(List.of(sample, sample, sample, sample));
                for (var result : results) { assertEquals(results.get(0).get(), result.get()); }
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void flatPresetUsesItsActualGeneratorAndHasNoDensityClaim() {
        try (var snapshot = snapshot(0, dimensions(WorldPresets.FLAT))) {
            var context = snapshot.dimensions().stream().filter(value -> value.dimension().id().value().equals("minecraft:overworld")).findFirst().orElseThrow();
            var backend = snapshot.backend(context.dimension().id());
            assertFalse(backend.capabilities(context).supports(DataLayer.CAVE_DENSITY));
            try (var sampler = backend.open(context)) {
                assertEquals(-60, sampler.height(0, 0, HeightMode.WORLD_SURFACE));
                assertEquals(-60, sampler.height(-257, 255, HeightMode.WORLD_SURFACE));
            }
        }
    }

    @Test void slimeChecksUseTheNativeSeedRule() {
        try (var snapshot = snapshot(-42)) {
            var context = snapshot.dimensions().get(0);
            try (var sampler = snapshot.backend(context.dimension().id()).open(context)) {
                for (int x = -20; x <= 20; x++) {
                    assertEquals(ChunkRandom.getSlimeRandom(x, -x, -42, 987234911L).nextInt(10) == 0, sampler.slimeChunk(x, -x));
                }
            }
        }
    }

    @Test void structureCandidatesRemainBoundedAndExplicitlyEstimated() {
        try (var snapshot = snapshot(42)) {
            var context = snapshot.dimensions().stream().filter(value -> value.dimension().id().value().equals("minecraft:overworld")).findFirst().orElseThrow();
            var request = new TileRequest(0, 0, 4096, 256, DataLayer.STRUCTURE_CANDIDATES, 0,
                    HeightMode.WORLD_SURFACE, new StructureQuery(java.util.Set.of(), 8));
            try (var sampler = snapshot.backend(context.dimension().id()).open(context)) {
                var tile = sampler.structures(new TileKey(context.fingerprint(), request), () -> false);
                assertFalse(tile.structures().isEmpty());
                assertTrue(tile.structures().size() <= 8);
                assertTrue(tile.structures().stream().allMatch(structure -> structure.accuracy() == SupportLevel.ESTIMATED));
            }
        }
    }

    @Test void changingSeedChangesIdentityAndClosingRejectsNewSamplers() {
        var first = snapshot(1);
        try (var second = snapshot(2)) {
            assertNotEquals(first.dimensions().get(0).fingerprint(), second.dimensions().get(0).fingerprint());
        }
        var context = first.dimensions().get(0);
        var factory = first.backend(context.dimension().id());
        first.close();
        assertThrows(IllegalStateException.class, () -> factory.open(context));
    }

    @Test void effectiveRegistryFingerprintIsDeterministic() {
        assertEquals(NativeRegistryFingerprint.capture(registries), NativeRegistryFingerprint.capture(registries));
    }

    @Test void disabledWorldStructuresAreNotAdvertisedAsPreviewCandidates() {
        try (var disabled = new NativeWorldSnapshot(registries, dimensions, 42, "fabric", "0.16.14",
                List.of("vanilla"), Map.of(), EDITOR, false); var enabled = snapshot(42)) {
            for (var context : disabled.dimensions()) {
                assertFalse(disabled.backend(context.dimension().id()).capabilities(context).supports(DataLayer.STRUCTURE_CANDIDATES));
                assertNotEquals(enabled.dimensions().stream().filter(other -> other.dimension().id().equals(context.dimension().id()))
                        .findFirst().orElseThrow().fingerprint(), context.fingerprint());
            }
        }
    }

    @Test void fabricAppliesTheVanillaCreationInvoker() {
        assertTrue(CreateWorldScreenInvoker.class.isAssignableFrom(CreateWorldScreen.class));
    }
}
