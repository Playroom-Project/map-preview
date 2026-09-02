package io.github.playroomproject.mappreview.minecraft.worldgen;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.WorldgenSampler;
import io.github.playroomproject.mappreview.core.tile.StructureTile;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Each worker owns its generator and NoiseConfig; no world or chunk is created by these operations. */
public final class NativeWorldgenSampler implements WorldgenSampler {
    private final ChunkGenerator generator;
    private final NoiseConfig noise;
    private final HeightLimitView heightLimit;
    private final Map<Biome, Integer> biomeIds;
    private final long seed;
    private final RegistryWrapper.WrapperLookup registries;
    private NativeStructureSampler structures;

    public NativeWorldgenSampler(RegistryWrapper.WrapperLookup registries, ChunkGenerator generator,
                                  PreviewContext context, Map<Biome, Integer> biomeIds) {
        this.generator = generator;
        this.registries = registries;
        this.biomeIds = biomeIds;
        seed = context.seed();
        var dimension = context.dimension();
        heightLimit = HeightLimitView.create(dimension.minY(), dimension.maxYExclusive() - dimension.minY());
        var settings = generator instanceof NoiseChunkGenerator noiseGenerator
                ? noiseGenerator.getSettings().value() : ChunkGeneratorSettings.createMissingSettings();
        noise = NoiseConfig.create(settings, registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
    }

    @Override public int biome(int x, int y, int z) {
        var biome = generator.getBiomeSource().getBiome(Math.floorDiv(x, 4), Math.floorDiv(y, 4),
                Math.floorDiv(z, 4), noise.getMultiNoiseSampler()).value();
        Integer id = biomeIds.get(biome);
        if (id == null) { throw new IllegalStateException("Generator returned a biome outside the active registry"); }
        return id;
    }
    @Override public int height(int x, int z, HeightMode mode) {
        return generator.getHeight(x, z, mode == HeightMode.OCEAN_FLOOR
                ? Heightmap.Type.OCEAN_FLOOR_WG : Heightmap.Type.WORLD_SURFACE_WG, heightLimit, noise);
    }
    @Override public int surface(int x, int z) {
        int y = height(x, z, HeightMode.WORLD_SURFACE) - 1;
        return Block.getRawIdFromState(generator.getColumnSample(x, z, heightLimit, noise).getState(y));
    }
    @Override public float caveDensity(int x, int y, int z) {
        return (float) noise.getNoiseRouter().finalDensity().sample(new DensityFunction.UnblendedNoisePos(x, y, z));
    }
    @Override public boolean slimeChunk(int chunkX, int chunkZ) {
        return ChunkRandom.getSlimeRandom(chunkX, chunkZ, seed, 987234911L).nextInt(10) == 0;
    }
    @Override public StructureTile structures(TileKey key, CancellationToken token) {
        if (structures == null) { structures = new NativeStructureSampler(registries, generator, noise, seed); }
        return structures.sample(key, token);
    }
    @Override public void column(int x, int z, int minY, int maxYExclusive, IntConsumer states, CancellationToken token) {
        var column = generator.getColumnSample(x, z, heightLimit, noise);
        for (int y = minY; y < maxYExclusive; y++) {
            token.check();
            states.accept(Block.getRawIdFromState(column.getState(y)));
        }
    }
}
