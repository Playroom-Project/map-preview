package io.github.playroomproject.mappreview.pregen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChunkPlanTest {
    @ParameterizedTest @EnumSource(ChunkPlan.Traversal.class)
    void allShapesEnumerateExactlyTheirAreaWithoutDuplicates(ChunkPlan.Traversal traversal) {
        var shapes = List.of(
                ChunkAreas.rectangle(new ChunkBounds(-4, -3, 6, 7)),
                ChunkAreas.circle(-5, 3, 7),
                ChunkAreas.circle(0, 0, 0),
                ChunkAreas.polygon(List.of(new ChunkPos(-4, -4), new ChunkPos(5, -4), new ChunkPos(5, 0),
                        new ChunkPos(0, 0), new ChunkPos(0, 5), new ChunkPos(-4, 5))));
        for (ChunkArea shape : shapes) {
            var plan = new ChunkPlan(shape, traversal);
            Set<ChunkPos> expected = new HashSet<>();
            var bounds = shape.bounds();
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    if (shape.contains(x, z)) { expected.add(new ChunkPos(x, z)); }
                }
            }
            var visited = new HashSet<ChunkPos>();
            var cursor = plan.cursor(0);
            ChunkPos position;
            while ((position = cursor.next()) != null) { assertTrue(visited.add(position), "Duplicate chunk " + position); }
            assertEquals(expected, visited);
            assertEquals(expected.size(), plan.totalChunks());
        }
    }

    @ParameterizedTest @EnumSource(ChunkPlan.Traversal.class)
    void restoringAtEveryCursorPositionDoesNotSkipOrRepeatChunks(ChunkPlan.Traversal traversal) {
        var plan = new ChunkPlan(ChunkAreas.circle(-3, -2, 4), traversal);
        var cursor = plan.cursor(0);
        var prefix = new ArrayList<ChunkPos>();
        ChunkPos current;
        while ((current = cursor.next()) != null) {
            prefix.add(current);
            assertEquals(prefix.size(), plan.acceptedBefore(cursor.offset()));
            assertEquals(cursor.offset() - 1, plan.indexOf(current));
            var resumed = plan.cursor(cursor.offset());
            var combined = new ArrayList<>(prefix);
            ChunkPos remainder;
            while ((remainder = resumed.next()) != null) { combined.add(remainder); }
            assertEquals(plan.totalChunks(), combined.size());
            assertEquals(combined.size(), new HashSet<>(combined).size());
        }
    }

    @Test void circleSpansMatchExactIntegerDistanceAtBoundaries() {
        for (int radius = 0; radius < 50; radius++) {
            var circle = ChunkAreas.circle(0, 0, radius);
            for (int z = -radius; z <= radius; z++) {
                ChunkSpan span = circle.rowSpans(z).get(0);
                assertTrue(circle.contains(span.minX(), z));
                assertTrue(circle.contains(span.maxX(), z));
                assertFalse(circle.contains(span.minX() - 1, z));
                assertFalse(circle.contains(span.maxX() + 1, z));
            }
        }
    }

    @Test void polygonUsesHalfOpenCenterInclusion() {
        var polygon = ChunkAreas.polygon(List.of(new ChunkPos(0, 0), new ChunkPos(2, 0), new ChunkPos(2, 2), new ChunkPos(0, 2)));
        var plan = new ChunkPlan(polygon, ChunkPlan.Traversal.ROW_MAJOR);
        assertEquals(4, plan.totalChunks());
        assertTrue(polygon.contains(0, 0));
        assertFalse(polygon.contains(2, 1));
    }

    @Test void traversalAndShapeArePartOfCheckpointIdentity() {
        var area = ChunkAreas.circle(0, 0, 2);
        assertNotEquals(new ChunkPlan(area, ChunkPlan.Traversal.SPIRAL).fingerprint(), new ChunkPlan(area, ChunkPlan.Traversal.ROW_MAJOR).fingerprint());
    }

    @Test void coordinateConversionsFloorRatherThanTruncate() {
        assertEquals(new ChunkPos(-1, -2), ChunkPos.fromBlocks(-1, -17));
        assertEquals(new ChunkPos(0, 1), ChunkPos.fromBlocks(15, 16));
        assertNotEquals(new ChunkPos(-1, 0).packed(), new ChunkPos(0, -1).packed());
    }

    @Test void invalidAndOverflowingAreasAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChunkAreas.circle(0, 0, -1));
        assertThrows(ArithmeticException.class, () -> ChunkAreas.circle(Integer.MAX_VALUE, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> ChunkAreas.polygon(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0))));
    }

    @ParameterizedTest @EnumSource(ChunkPlan.Traversal.class)
    void everyRawPrefixMatchesTheNumberOfAcceptedCoordinates(ChunkPlan.Traversal traversal) {
        var area = ChunkAreas.polygon(List.of(new ChunkPos(-6, -3), new ChunkPos(3, -3), new ChunkPos(0, 0), new ChunkPos(-6, 4)));
        var plan = new ChunkPlan(area, traversal);
        var indices = new ArrayList<Long>();
        var cursor = plan.cursor(0);
        ChunkPos position;
        while ((position = cursor.next()) != null) { indices.add(plan.indexOf(position)); }
        for (long offset = 0; offset <= plan.candidateCount(); offset++) {
            long limit = offset;
            assertEquals(indices.stream().filter(index -> index < limit).count(), plan.acceptedBefore(offset), "Offset " + offset);
        }
    }

    @Test void aLargeRectangleIsCountedWithoutEnumeratingRowsOrChunks() {
        var plan = new ChunkPlan(ChunkAreas.rectangle(new ChunkBounds(-1_000_000, -1_000_000, 1_000_000, 1_000_000)), ChunkPlan.Traversal.ROW_MAJOR);
        assertEquals(4_000_004_000_001L, plan.totalChunks());
        assertEquals(3_000_000_000_000L, plan.acceptedBefore(3_000_000_000_000L));
    }
}
