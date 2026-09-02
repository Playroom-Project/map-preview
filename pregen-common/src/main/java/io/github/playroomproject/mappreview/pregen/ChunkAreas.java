package io.github.playroomproject.mappreview.pregen;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Rectangle, circle and even-odd polygon shapes. Spiral is a traversal order, not a different area. */
public final class ChunkAreas {
    private ChunkAreas() { }

    public static ChunkArea rectangle(ChunkBounds bounds) { return new Rectangle(bounds); }
    public static ChunkArea circle(int centerX, int centerZ, int radiusChunks) { return new Circle(centerX, centerZ, radiusChunks); }

    /** Integer polygon vertices describe chunk-grid edges; chunks are included by their centers. */
    public static ChunkArea polygon(List<ChunkPos> vertices) { return new Polygon(vertices); }

    private record Rectangle(ChunkBounds bounds) implements ChunkArea {
        @Override public long totalChunks() { return bounds.area(); }
        @Override public List<ChunkSpan> rowSpans(int z) {
            return z < bounds.minZ() || z > bounds.maxZ() ? List.of() : List.of(new ChunkSpan(bounds.minX(), bounds.maxX()));
        }
        @Override public boolean contains(int x, int z) { return bounds.contains(x, z); }
        @Override public Fingerprint fingerprint() {
            return Fingerprint.builder().add("rectangle-v1").add(bounds.minX()).add(bounds.minZ()).add(bounds.maxX()).add(bounds.maxZ()).finish();
        }
    }

    private static final class Circle implements ChunkArea {
        private final int x;
        private final int z;
        private final int radius;
        private final ChunkBounds bounds;

        private Circle(int x, int z, int radius) {
            if (radius < 0 || radius > 1_000_000_000) { throw new IllegalArgumentException("Invalid circle radius"); }
            this.x = x;
            this.z = z;
            this.radius = radius;
            bounds = new ChunkBounds(Math.subtractExact(x, radius), Math.subtractExact(z, radius),
                    Math.addExact(x, radius), Math.addExact(z, radius));
        }
        @Override public ChunkBounds bounds() { return bounds; }
        @Override public long totalChunks() {
            long count = 2L * radius + 1;
            long dx = radius;
            long squared = (long) radius * radius;
            for (long dz = 1; dz <= radius; dz++) {
                while (dx * dx + dz * dz > squared) { dx--; }
                count += 2 * (2 * dx + 1);
            }
            return count;
        }
        @Override public boolean contains(int px, int pz) {
            if (!bounds.contains(px, pz)) { return false; }
            long dx = (long) px - x;
            long dz = (long) pz - z;
            return dx * dx + dz * dz <= (long) radius * radius;
        }
        @Override public List<ChunkSpan> rowSpans(int row) {
            if (row < bounds.minZ() || row > bounds.maxZ()) { return List.of(); }
            long dz = (long) row - z;
            long remaining = (long) radius * radius - dz * dz;
            long dx = (long) Math.sqrt(remaining);
            while ((dx + 1) * (dx + 1) <= remaining) { dx++; }
            while (dx * dx > remaining) { dx--; }
            return List.of(new ChunkSpan((int) (x - dx), (int) (x + dx)));
        }
        @Override public Fingerprint fingerprint() { return Fingerprint.builder().add("circle-v1").add(x).add(z).add(radius).finish(); }
    }

    private static final class Polygon implements ChunkArea {
        private final List<ChunkPos> vertices;
        private final ChunkBounds bounds;

        private Polygon(List<ChunkPos> vertices) {
            if (vertices.size() < 3 || vertices.size() > 1024) { throw new IllegalArgumentException("Polygons need 3 to 1024 vertices"); }
            this.vertices = List.copyOf(vertices);
            int minX = vertices.stream().mapToInt(ChunkPos::x).min().orElseThrow();
            int maxX = vertices.stream().mapToInt(ChunkPos::x).max().orElseThrow();
            int minZ = vertices.stream().mapToInt(ChunkPos::z).min().orElseThrow();
            int maxZ = vertices.stream().mapToInt(ChunkPos::z).max().orElseThrow();
            if (minX == maxX || minZ == maxZ) { throw new IllegalArgumentException("Polygon has no area"); }
            bounds = new ChunkBounds(minX, minZ, maxX - 1, maxZ - 1);
        }
        @Override public ChunkBounds bounds() { return bounds; }
        @Override public List<ChunkSpan> rowSpans(int z) {
            if (z < bounds.minZ() || z > bounds.maxZ()) { return List.of(); }
            double scan = z + 0.5;
            double[] crossings = new double[vertices.size()];
            int count = 0;
            for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
                ChunkPos a = vertices.get(i);
                ChunkPos b = vertices.get(j);
                if ((a.z() > scan) != (b.z() > scan)) {
                    crossings[count++] = a.x() + (scan - a.z()) * ((double) b.x() - a.x()) / ((double) b.z() - a.z());
                }
            }
            Arrays.sort(crossings, 0, count);
            var spans = new ArrayList<ChunkSpan>(count / 2);
            for (int i = 0; i + 1 < count; i += 2) {
                int left = (int) Math.ceil(crossings[i] - 0.5);
                int right = (int) (Math.ceil(crossings[i + 1] - 0.5) - 1);
                if (left <= right) { spans.add(new ChunkSpan(left, right)); }
            }
            return List.copyOf(spans);
        }
        @Override public boolean contains(int x, int z) {
            if (!bounds.contains(x, z)) { return false; }
            double px = x + 0.5;
            double pz = z + 0.5;
            boolean inside = false;
            for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
                ChunkPos a = vertices.get(i);
                ChunkPos b = vertices.get(j);
                if ((a.z() > pz) != (b.z() > pz)
                        && px < a.x() + (pz - a.z()) * ((double) b.x() - a.x()) / ((double) b.z() - a.z())) {
                    inside = !inside;
                }
            }
            return inside;
        }
        @Override public Fingerprint fingerprint() {
            var hash = Fingerprint.builder().add("polygon-center-even-odd-v1").add(vertices.size());
            vertices.forEach(p -> hash.add(p.x()).add(p.z()));
            return hash.finish();
        }
    }
}
