package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.List;

/** Immutable shapes return sorted, disjoint row spans inside their declared bounds. */
public interface ChunkArea {
    ChunkBounds bounds();
    List<ChunkSpan> rowSpans(int z);
    Fingerprint fingerprint();

    default long totalChunks() {
        long count = 0;
        ChunkBounds bounds = bounds();
        for (long z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            long last = (long) bounds.minX() - 1;
            for (ChunkSpan span : rowSpans((int) z)) {
                if (span.minX() <= last || span.minX() < bounds.minX() || span.maxX() > bounds.maxX()) {
                    throw new IllegalArgumentException("Shape returned overlapping or out-of-bounds spans");
                }
                count = Math.addExact(count, span.size());
                last = span.maxX();
            }
        }
        return count;
    }

    default boolean contains(int x, int z) {
        if (!bounds().contains(x, z)) { return false; }
        for (ChunkSpan span : rowSpans(z)) { if (x >= span.minX() && x <= span.maxX()) { return true; } }
        return false;
    }
}
