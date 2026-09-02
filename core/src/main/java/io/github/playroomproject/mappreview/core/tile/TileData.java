package io.github.playroomproject.mappreview.core.tile;

/** Immutable CPU data. Byte weights include a conservative allowance for cache entry overhead. */
public sealed interface TileData permits RasterTile, StructureTile {
    TileKey key();
    long byteSize();
}
