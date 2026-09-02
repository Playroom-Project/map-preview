package io.github.playroomproject.mappreview.client.render;

import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.core.filter.PreviewFilter;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.RasterTile;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Height views share the same halo samples; recoloring performs zero world generation calls. */
public final class TileColorizer {
    public enum Style { BIOMES, HEIGHT, TOPOGRAPHY, SLOPE, LAND_OCEAN }
    private TileColorizer() { }

    public static ColoredTile colorize(RasterTile biomes, RasterTile heights, BiomeColors colors,
                                       Style style, int seaLevel, PreviewFilter filter) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(filter, "filter");
        if ((style == Style.BIOMES || filter.needsBiomes()) && (biomes == null || colors == null)) {
            throw new IllegalArgumentException("This view needs biome data and colors");
        }
        if ((style != Style.BIOMES || filter.needsHeights()) && heights == null) {
            throw new IllegalArgumentException("This view needs height data");
        }
        validateLayers(biomes, heights);
        RasterTile primary = style == Style.BIOMES ? biomes : heights;
        TileRequest request = Objects.requireNonNull(primary, "primary tile").key().request();
        int cells = request.cells();
        int[] pixels = new int[cells * cells];
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int biome = biomes == null ? -1 : biomes.value(x, z);
                int height = heights == null ? 0 : heights.value(x + 1, z + 1);
                double slope = 0;
                double dx = 0;
                double dz = 0;
                if (heights != null) {
                    dx = ((double) heights.value(x + 2, z + 1) - heights.value(x, z + 1)) / (2 * request.step());
                    dz = ((double) heights.value(x + 1, z + 2) - heights.value(x + 1, z)) / (2 * request.step());
                    slope = Math.toDegrees(Math.atan(Math.hypot(dx, dz)));
                }
                int color = switch (style) {
                    case BIOMES -> colors.argb(biome);
                    case HEIGHT -> gray(Math.max(0, Math.min(255, 128 + (height - (double) seaLevel) * 0.5)));
                    case SLOPE -> gray(slope * 255 / 90);
                    case LAND_OCEAN -> height < seaLevel ? 0xff376cba : 0xff8caf65;
                    case TOPOGRAPHY -> {
                        int base = height < seaLevel ? 0xff376cba : 0xff8caf65;
                        double light = Math.max(0.35, Math.min(1.2, 0.8 + (-dx - dz) / (4 * Math.sqrt(dx * dx + dz * dz + 1))));
                        if (Math.floorMod(height, 16) < Math.max(1, request.step() / 4)) { light *= 0.65; }
                        yield shade(base, light);
                    }
                };
                pixels[z * cells + x] = filter.color(biome, height, slope, color);
            }
        }
        return new ColoredTile(primary.key(), cells, pixels);
    }

    /** For block-state palettes, density slices and other explicitly supported raster channels. */
    public static ColoredTile raw(RasterTile tile, IntUnaryOperator palette) {
        int cells = tile.key().request().cells();
        int border = tile.key().request().layer().border();
        int[] pixels = new int[cells * cells];
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) { pixels[z * cells + x] = palette.applyAsInt(tile.value(x + border, z + border)); }
        }
        return new ColoredTile(tile.key(), cells, pixels);
    }

    private static void validateLayers(RasterTile biomes, RasterTile heights) {
        if (biomes != null && biomes.key().request().layer() != DataLayer.BIOMES
                && biomes.key().request().layer() != DataLayer.CAVE_BIOMES) {
            throw new IllegalArgumentException("Expected biome raster");
        }
        if (heights != null && heights.key().request().layer() != DataLayer.HEIGHT) {
            throw new IllegalArgumentException("Expected height raster");
        }
        if (biomes != null && heights != null) {
            var a = biomes.key().request();
            var b = heights.key().request();
            if (!biomes.key().sessionFingerprint().equals(heights.key().sessionFingerprint())
                    || a.tileX() != b.tileX() || a.tileZ() != b.tileZ() || a.tileSize() != b.tileSize() || a.step() != b.step()) {
                throw new IllegalArgumentException("Cannot combine tiles from different sessions or grids");
            }
        }
    }

    private static int gray(double value) { int v = (int) value; return 0xff000000 | v << 16 | v << 8 | v; }
    private static int shade(int argb, double light) {
        int r = Math.min(255, (int) (((argb >>> 16) & 255) * light));
        int g = Math.min(255, (int) (((argb >>> 8) & 255) * light));
        int b = Math.min(255, (int) ((argb & 255) * light));
        return 0xff000000 | r << 16 | g << 8 | b;
    }
}
