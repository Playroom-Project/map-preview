package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.tile.RasterTile;
import io.github.playroomproject.mappreview.core.tile.TileData;
import io.github.playroomproject.mappreview.core.tile.TileKey;

/** Allocation-free sample loops; only the requested channel is evaluated. */
public final class TileSampling {
    private TileSampling() { }

    public static TileData sample(WorldgenSampler sampler, TileKey key, PreviewContext context, CancellationToken token) {
        token.check();
        var request = key.request();
        if (request.layer().structures()) { return sampler.structures(key, token); }
        if (request.layer().usesY() && !context.dimension().containsY(request.y())) {
            throw new IllegalArgumentException("Requested Y is outside the selected dimension");
        }
        var buffer = RasterTile.builder(key);
        int side = request.samplesPerSide();
        int index = 0;
        for (int row = 0; row < side; row++) {
            token.check();
            int z = request.sampleZ(row);
            for (int column = 0; column < side; column++) {
                if ((column & 31) == 0) { token.check(); }
                int x = request.sampleX(column);
                int value = switch (request.layer()) {
                    case BIOMES, CAVE_BIOMES -> {
                        int biome = sampler.biome(x, request.y(), z);
                        if (biome < 0 || biome >= context.biomes().size()) {
                            throw new IllegalStateException("Backend returned an invalid session-local biome ID");
                        }
                        yield biome;
                    }
                    case HEIGHT -> sampler.height(x, z, request.heightMode());
                    case SURFACE -> sampler.surface(x, z);
                    case CAVE_DENSITY -> Float.floatToIntBits(sampler.caveDensity(x, request.y(), z));
                    case CAVE_BLOCKS -> sampler.accurateCaveBlock(x, request.y(), z);
                    case SLIME_CHUNKS -> sampler.slimeChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) ? 1 : 0;
                    case STRUCTURE_CANDIDATES, VERIFIED_STRUCTURES -> throw new AssertionError("Non-raster layer");
                };
                buffer.set(index++, value);
            }
        }
        token.check();
        return buffer.freeze();
    }
}
