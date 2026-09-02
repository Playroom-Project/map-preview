package io.github.playroomproject.mappreview.client.render;

import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.RasterTile;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Coarse terrain geometry in tile-local coordinates; no chunks, block models or GPU calls. */
public final class TerrainMesh {
    private final int originX;
    private final int originZ;
    private final float[] vertices;
    private final int[] indices;

    private TerrainMesh(int originX, int originZ, float[] vertices, int[] indices) {
        this.originX = originX;
        this.originZ = originZ;
        this.vertices = vertices;
        this.indices = indices;
    }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int vertexCount() { return vertices.length / 3; }
    public int triangleCount() { return indices.length / 3; }
    public FloatBuffer vertices() { return FloatBuffer.wrap(vertices).asReadOnlyBuffer(); }
    public IntBuffer indices() { return IntBuffer.wrap(indices).asReadOnlyBuffer(); }

    public static TerrainMesh fromHeightTile(RasterTile height) {
        var request = height.key().request();
        if (request.layer() != DataLayer.HEIGHT || request.cells() > 256) {
            throw new IllegalArgumentException("Terrain meshes require height data with at most 256 cells per side");
        }
        int cells = request.cells();
        int side = cells + 1;
        float[] vertices = new float[side * side * 3];
        int[] indices = new int[cells * cells * 6];
        int index = 0;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                vertices[index++] = x * request.step();
                vertices[index++] = height.value(x + 1, z + 1);
                vertices[index++] = z * request.step();
            }
        }
        index = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int top = z * side + x;
                indices[index++] = top;
                indices[index++] = top + side;
                indices[index++] = top + 1;
                indices[index++] = top + 1;
                indices[index++] = top + side;
                indices[index++] = top + side + 1;
            }
        }
        return new TerrainMesh(request.originX(), request.originZ(), vertices, indices);
    }
}
