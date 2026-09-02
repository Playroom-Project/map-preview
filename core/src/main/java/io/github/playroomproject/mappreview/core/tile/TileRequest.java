package io.github.playroomproject.mappreview.core.tile;

import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.StructureQuery;
import java.util.Objects;

/** Tile coordinates use floor division, including west/north of world origin. */
public record TileRequest(int tileX, int tileZ, int tileSize, int step, DataLayer layer,
                          int y, HeightMode heightMode, StructureQuery structures) {
    public TileRequest {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(heightMode, "heightMode");
        Objects.requireNonNull(structures, "structures");
        if (tileSize < 16 || tileSize > 1_048_576 || Integer.bitCount(tileSize) != 1
                || step < 1 || step > tileSize || Integer.bitCount(step) != 1 || tileSize / step > 1024) {
            throw new IllegalArgumentException("Tile size and sample step must be supported powers of two");
        }
        if (!layer.usesY()) { y = 0; }
        if (layer != DataLayer.HEIGHT) { heightMode = HeightMode.WORLD_SURFACE; }
        if (!layer.structures()) { structures = StructureQuery.DEFAULT; }
        int border = layer.border();
        long x = (long) tileX * tileSize;
        long z = (long) tileZ * tileSize;
        if (x - (long) border * step < Integer.MIN_VALUE || z - (long) border * step < Integer.MIN_VALUE
                || x + tileSize + (long) border * step > Integer.MAX_VALUE
                || z + tileSize + (long) border * step > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Tile samples exceed supported block coordinates");
        }
    }

    public static TileRequest of(int x, int z, int step, DataLayer layer, int y) {
        return new TileRequest(x, z, 256, step, layer, y, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT);
    }

    public int originX() { return Math.multiplyExact(tileX, tileSize); }
    public int originZ() { return Math.multiplyExact(tileZ, tileSize); }
    public int cells() { return tileSize / step; }
    public int samplesPerSide() { return cells() + 2 * layer.border(); }
    public int sampleX(int column) { return originX() + (column - layer.border()) * step; }
    public int sampleZ(int row) { return originZ() + (row - layer.border()) * step; }
}
