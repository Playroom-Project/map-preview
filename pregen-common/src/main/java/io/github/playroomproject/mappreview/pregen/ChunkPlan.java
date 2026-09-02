package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.List;
import java.util.Objects;

/** Streaming enumeration and exact totals use O(vertices) memory, independent of chunk count. */
public final class ChunkPlan {
    public enum Traversal { ROW_MAJOR, SPIRAL }
    private final ChunkArea area;
    private final Traversal traversal;
    private final long total;
    private final long candidateCount;
    private final long centerX;
    private final long centerZ;
    private final Fingerprint fingerprint;

    public ChunkPlan(ChunkArea area, Traversal traversal) {
        this.area = Objects.requireNonNull(area, "area");
        this.traversal = Objects.requireNonNull(traversal, "traversal");
        ChunkBounds bounds = area.bounds();
        long count = area.totalChunks();
        if (count == 0) { throw new IllegalArgumentException("Shape contains no chunks"); }
        total = count;
        centerX = Math.floorDiv((long) bounds.minX() + bounds.maxX(), 2);
        centerZ = Math.floorDiv((long) bounds.minZ() + bounds.maxZ(), 2);
        long radius = Math.max(Math.max(centerX - bounds.minX(), bounds.maxX() - centerX),
                Math.max(centerZ - bounds.minZ(), bounds.maxZ() - centerZ));
        candidateCount = traversal == Traversal.ROW_MAJOR ? bounds.area() : Math.multiplyExact(2 * radius + 1, 2 * radius + 1);
        fingerprint = Fingerprint.builder().add("chunk-plan-v1").add(area.fingerprint().hex()).add(traversal.name()).finish();
    }

    public long totalChunks() { return total; }
    public long candidateCount() { return candidateCount; }
    public Fingerprint fingerprint() { return fingerprint; }
    public ChunkArea area() { return area; }
    public Cursor cursor(long offset) { return new Cursor(offset); }

    /** Raw traversal index, used to reject checkpoint retries that were never dispatched. */
    public long indexOf(ChunkPos position) {
        if (!area.contains(position.x(), position.z())) { throw new IllegalArgumentException("Chunk is outside the plan"); }
        if (traversal == Traversal.ROW_MAJOR) {
            return ((long) position.z() - area.bounds().minZ()) * area.bounds().width() + position.x() - area.bounds().minX();
        }
        long x = position.x() - centerX;
        long z = position.z() - centerZ;
        long ring = Math.max(Math.abs(x), Math.abs(z));
        if (ring == 0) { return 0; }
        long side = 2 * ring;
        long distance;
        if (z == -ring) { distance = ring - x; }
        else if (x == -ring) { distance = side + z + ring; }
        else if (z == ring) { distance = 2 * side + x + ring; }
        else { distance = 3 * side + ring - z; }
        return (side + 1) * (side + 1) - 1 - distance;
    }

    /** Count a checkpoint prefix by spans and at most one perimeter, without replaying its chunks. */
    public long acceptedBefore(long offset) {
        if (offset < 0 || offset > candidateCount) { throw new IllegalArgumentException("Invalid cursor checkpoint"); }
        if (offset == 0) { return 0; }
        if (offset == candidateCount) { return total; }
        ChunkBounds bounds = area.bounds();
        if (traversal == Traversal.ROW_MAJOR) {
            long fullRows = offset / bounds.width();
            long count = countBox(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.minZ() + fullRows - 1);
            return count + countBox(bounds.minX(), bounds.minZ() + fullRows,
                    bounds.minX() + offset % bounds.width() - 1, bounds.minZ() + fullRows);
        }
        if (offset == 1) { return area.contains((int) centerX, (int) centerZ) ? 1 : 0; }
        long ring = (long) Math.ceil((Math.sqrt(offset) - 1) / 2);
        while ((2 * ring + 1) * (2 * ring + 1) < offset) { ring++; }
        while (ring > 0 && (2 * ring - 1) * (2 * ring - 1) >= offset) { ring--; }
        long side = 2 * ring;
        long remaining = offset - (side - 1) * (side - 1);
        long count = countBox(centerX - ring + 1, centerZ - ring + 1, centerX + ring - 1, centerZ + ring - 1);
        long segment = Math.min(side, remaining);
        count += countBox(centerX + ring, centerZ - ring + 1, centerX + ring, centerZ - ring + segment);
        remaining -= segment;
        segment = Math.min(side, remaining);
        count += countBox(centerX + ring - segment, centerZ + ring, centerX + ring - 1, centerZ + ring);
        remaining -= segment;
        segment = Math.min(side, remaining);
        count += countBox(centerX - ring, centerZ + ring - segment, centerX - ring, centerZ + ring - 1);
        remaining -= segment;
        count += countBox(centerX - ring + 1, centerZ - ring, centerX - ring + remaining, centerZ - ring);
        return count;
    }

    private long countBox(long minX, long minZ, long maxX, long maxZ) {
        ChunkBounds bounds = area.bounds();
        minX = Math.max(minX, bounds.minX()); maxX = Math.min(maxX, bounds.maxX());
        minZ = Math.max(minZ, bounds.minZ()); maxZ = Math.min(maxZ, bounds.maxZ());
        if (minX > maxX || minZ > maxZ) { return 0; }
        if (total == bounds.area()) { return Math.multiplyExact(maxX - minX + 1, maxZ - minZ + 1); }
        long count = 0;
        for (long z = minZ; z <= maxZ; z++) {
            for (ChunkSpan span : area.rowSpans((int) z)) {
                count += Math.max(0, Math.min(maxX, span.maxX()) - Math.max(minX, span.minX()) + 1);
            }
        }
        return count;
    }

    public final class Cursor {
        private long offset;
        private long cachedRow = Long.MIN_VALUE;
        private List<ChunkSpan> spans = List.of();
        private int spanIndex;

        private Cursor(long offset) {
            if (offset < 0 || offset > candidateCount) { throw new IllegalArgumentException("Invalid cursor checkpoint"); }
            this.offset = offset;
        }
        public long offset() { return offset; }
        public boolean exhausted() { return offset >= candidateCount; }

        /** Returns null at exhaustion; only accepted, bounded in-flight positions become objects. */
        public ChunkPos next() {
            while (!exhausted()) {
                ChunkPos position = poll(4096);
                if (position != null) { return position; }
            }
            return null;
        }

        /** Null means either exhaustion or a consumed scan budget; consult exhausted() before finishing a job. */
        public ChunkPos poll(int scanBudget) {
            if (scanBudget < 1) { throw new IllegalArgumentException("A cursor scan needs a positive budget"); }
            if (traversal == Traversal.SPIRAL) { return nextSpiral(scanBudget); }
            ChunkBounds bounds = area.bounds();
            while (offset < candidateCount && scanBudget-- > 0) {
                long row = offset / bounds.width();
                int z = (int) (bounds.minZ() + row);
                if (row != cachedRow) { spans = area.rowSpans(z); spanIndex = 0; cachedRow = row; }
                long x = bounds.minX() + offset % bounds.width();
                while (spanIndex < spans.size() && x > spans.get(spanIndex).maxX()) { spanIndex++; }
                if (spanIndex == spans.size()) { offset = (row + 1) * bounds.width(); continue; }
                ChunkSpan span = spans.get(spanIndex);
                x = Math.max(x, span.minX());
                offset = row * bounds.width() + x - bounds.minX() + 1;
                return new ChunkPos((int) x, z);
            }
            return null;
        }

        private ChunkPos nextSpiral(int scanBudget) {
            while (offset < candidateCount && scanBudget-- > 0) {
                long n = offset++;
                long x = 0;
                long z = 0;
                if (n != 0) {
                    long ring = (long) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
                    while ((2 * ring + 1) * (2 * ring + 1) <= n) { ring++; }
                    while (ring > 0 && (2 * ring - 1) * (2 * ring - 1) > n) { ring--; }
                    long side = 2 * ring;
                    long distance = (side + 1) * (side + 1) - 1 - n;
                    if (distance < side) { x = ring - distance; z = -ring; }
                    else if (distance < 2 * side) { x = -ring; z = -ring + distance - side; }
                    else if (distance < 3 * side) { x = -ring + distance - 2 * side; z = ring; }
                    else { x = ring; z = ring - distance + 3 * side; }
                }
                x += centerX;
                z += centerZ;
                if (x >= Integer.MIN_VALUE && x <= Integer.MAX_VALUE && z >= Integer.MIN_VALUE && z <= Integer.MAX_VALUE
                        && area.contains((int) x, (int) z)) { return new ChunkPos((int) x, (int) z); }
            }
            return null;
        }
    }
}
