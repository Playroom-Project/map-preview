package io.github.playroomproject.mappreview.client.render;

import io.github.playroomproject.mappreview.core.tile.TileKey;
import java.nio.IntBuffer;

/** Immutable ARGB CPU pixels. Native RGBA conversion belongs to the rendering adapter. */
public final class ColoredTile {
    private final TileKey key;
    private final int side;
    private final int[] argb;

    ColoredTile(TileKey key, int side, int[] argb) { this.key = key; this.side = side; this.argb = argb; }
    public TileKey key() { return key; }
    public int side() { return side; }
    public int pixel(int index) { return argb[index]; }
    public IntBuffer pixels() { return IntBuffer.wrap(argb).asReadOnlyBuffer(); }
    public long byteSize() { return key.byteSize() + 128L + 4L * argb.length; }
}
