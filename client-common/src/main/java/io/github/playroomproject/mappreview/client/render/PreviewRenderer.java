package io.github.playroomproject.mappreview.client.render;

import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.tile.TileKey;

/** All methods run on the render thread. Implementations own a separate, bounded GPU cache. */
public interface PreviewRenderer extends AutoCloseable {
    void upload(ColoredTile tile);
    void draw(PreviewCamera camera);
    void release(TileKey key);
    @Override void close();
}
