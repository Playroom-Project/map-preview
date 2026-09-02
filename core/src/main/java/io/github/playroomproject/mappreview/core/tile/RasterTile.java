package io.github.playroomproject.mappreview.core.tile;

import java.nio.IntBuffer;
import java.util.Objects;

/** Primitive storage; integer heights preserve tall modded dimensions without short overflow. */
public final class RasterTile implements TileData {
    private final TileKey key;
    private final int[] values;

    private RasterTile(TileKey key, int[] values) { this.key = key; this.values = values; }
    @Override public TileKey key() { return key; }
    @Override public long byteSize() { return key.byteSize() + 128L + 4L * values.length; }
    public int side() { return key.request().samplesPerSide(); }
    public int size() { return values.length; }
    public int value(int index) { return values[index]; }
    public int value(int column, int row) { return values[row * side() + column]; }
    public IntBuffer values() { return IntBuffer.wrap(values).asReadOnlyBuffer(); }
    public static Builder builder(TileKey key) { return new Builder(key); }

    /** A worker-owned buffer. Freezing transfers ownership without copying and disables writes. */
    public static final class Builder {
        private final TileKey key;
        private int[] values;

        private Builder(TileKey key) {
            this.key = Objects.requireNonNull(key, "key");
            if (key.request().layer().structures()) { throw new IllegalArgumentException("Structures are not raster data"); }
            int side = key.request().samplesPerSide();
            values = new int[Math.multiplyExact(side, side)];
        }

        public void set(int index, int value) {
            if (values == null) { throw new IllegalStateException("Tile buffer has been frozen"); }
            values[index] = value;
        }

        public RasterTile freeze() {
            if (values == null) { throw new IllegalStateException("Tile buffer has been frozen"); }
            RasterTile tile = new RasterTile(key, values);
            values = null;
            return tile;
        }
    }
}
