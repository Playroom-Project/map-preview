package io.github.playroomproject.mappreview.core.scheduler;

import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.StructureQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Produces a bounded, screen-driven tile pyramid instead of enumerating an unbounded world. */
public final class ViewportPlanner {
    private final int baseTileSize;
    private final List<Integer> baseSteps;
    private final int prefetchRings;
    private final int maximumTiles;

    public ViewportPlanner(int baseTileSize, List<Integer> coarseToFineSteps, int prefetchRings, int maximumTiles) {
        if (baseTileSize < 16 || baseTileSize > 1024 || Integer.bitCount(baseTileSize) != 1
                || coarseToFineSteps.isEmpty() || prefetchRings < 0 || prefetchRings > 1
                || maximumTiles < 1 || maximumTiles > 16_384) {
            throw new IllegalArgumentException("Invalid viewport planning limits");
        }
        int previous = baseTileSize + 1;
        for (int step : coarseToFineSteps) {
            if (step < 1 || step >= previous || Integer.bitCount(step) != 1 || step > baseTileSize) {
                throw new IllegalArgumentException("LOD steps must be descending powers of two");
            }
            previous = step;
        }
        this.baseTileSize = baseTileSize;
        baseSteps = List.copyOf(coarseToFineSteps);
        this.prefetchRings = prefetchRings;
        this.maximumTiles = maximumTiles;
    }

    public List<PlannedTile> plan(PreviewCamera camera, DataLayer layer, int y,
                                  HeightMode heightMode, StructureQuery structures) {
        int scale = 1;
        while (baseTileSize * (double) scale / camera.blocksPerPixel() < 64 && baseTileSize * scale < 1_048_576) {
            scale *= 2;
        }
        int span = baseTileSize * scale;
        int minX = floorTile(camera.worldX(0), span);
        int maxX = floorTile(Math.nextDown(camera.worldX(camera.width())), span);
        int minZ = floorTile(camera.worldZ(0), span);
        int maxZ = floorTile(Math.nextDown(camera.worldZ(camera.height())), span);
        long count = ((long) maxX - minX + 1 + 2 * prefetchRings) * ((long) maxZ - minZ + 1 + 2 * prefetchRings);
        if (count > maximumTiles) {
            throw new IllegalArgumentException("Viewport exceeds its tile budget; reduce viewport size or increase the budget");
        }
        var result = new ArrayList<PlannedTile>();
        for (int pass = 0; pass < baseSteps.size(); pass++) {
            int step = baseSteps.get(pass) * scale;
            for (int z = minZ - prefetchRings; z <= maxZ + prefetchRings; z++) {
                for (int x = minX - prefetchRings; x <= maxX + prefetchRings; x++) {
                    boolean prefetch = x < minX || x > maxX || z < minZ || z > maxZ;
                    if (prefetch && pass > 0) { continue; }
                    double dx = ((double) x + 0.5) * span - camera.centerX();
                    double dz = ((double) z + 0.5) * span - camera.centerZ();
                    result.add(new PlannedTile(new TileRequest(x, z, span, step, layer, y, heightMode, structures),
                            new WorkPriority(prefetch, pass, dx * dx + dz * dz)));
                }
            }
            if (step <= camera.blocksPerPixel() || layer.structures()) { break; }
        }
        result.sort(Comparator.comparing(PlannedTile::priority));
        return List.copyOf(result);
    }

    private static int floorTile(double coordinate, int span) { return (int) Math.floor(coordinate / span); }
    public record PlannedTile(TileRequest request, WorkPriority priority) { }
}
