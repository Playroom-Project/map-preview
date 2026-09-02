package io.github.playroomproject.mappreview.pregen;

/** Chunk coordinates. Block-to-chunk conversion always floors, including negative coordinates. */
public record ChunkPos(int x, int z) {
    public static ChunkPos fromBlocks(int x, int z) { return new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)); }
    public long packed() { return (long) x << 32 | z & 0xffffffffL; }
}
