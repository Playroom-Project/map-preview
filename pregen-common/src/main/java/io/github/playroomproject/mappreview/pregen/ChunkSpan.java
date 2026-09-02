package io.github.playroomproject.mappreview.pregen;

/** One inclusive, contiguous X interval on a chunk row. */
public record ChunkSpan(int minX, int maxX) {
    public ChunkSpan { if (minX > maxX) { throw new IllegalArgumentException("Empty chunk span"); } }
    public long size() { return (long) maxX - minX + 1; }
}
