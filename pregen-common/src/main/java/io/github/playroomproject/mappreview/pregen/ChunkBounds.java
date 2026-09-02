package io.github.playroomproject.mappreview.pregen;

/** Inclusive bounds with checked long arithmetic; a plan never materializes all its coordinates. */
public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {
    public ChunkBounds {
        if (minX > maxX || minZ > maxZ) { throw new IllegalArgumentException("Empty chunk bounds"); }
        Math.multiplyExact((long) maxX - minX + 1, (long) maxZ - minZ + 1);
    }
    public long width() { return (long) maxX - minX + 1; }
    public long height() { return (long) maxZ - minZ + 1; }
    public long area() { return Math.multiplyExact(width(), height()); }
    public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
}
