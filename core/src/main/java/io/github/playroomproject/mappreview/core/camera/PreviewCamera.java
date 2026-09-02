package io.github.playroomproject.mappreview.core.camera;

/** Double-precision map camera; screen origin is top-left and increasing Z points down. */
public record PreviewCamera(double centerX, double centerZ, double blocksPerPixel, int width, int height) {
    public PreviewCamera {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(blocksPerPixel)
                || blocksPerPixel < 0.125 || blocksPerPixel > 16_384 || width < 1 || height < 1
                || width > 16_384 || height > 16_384 || Math.abs(centerX) > 30_000_000 || Math.abs(centerZ) > 30_000_000) {
            throw new IllegalArgumentException("Invalid Map PreView camera");
        }
    }

    public double worldX(double pixelX) { return centerX + (pixelX - width * 0.5) * blocksPerPixel; }
    public double worldZ(double pixelY) { return centerZ + (pixelY - height * 0.5) * blocksPerPixel; }

    public PreviewCamera pan(double pixelDeltaX, double pixelDeltaY) {
        return new PreviewCamera(clampCenter(centerX - pixelDeltaX * blocksPerPixel),
                clampCenter(centerZ - pixelDeltaY * blocksPerPixel), blocksPerPixel, width, height);
    }

    /** Keeps the world position beneath the pointer fixed while crossing discrete LOD thresholds. */
    public PreviewCamera zoomAt(double pixelX, double pixelY, double factor) {
        if (!Double.isFinite(factor) || factor <= 0) { throw new IllegalArgumentException("Invalid zoom factor"); }
        double scale = Math.max(0.125, Math.min(16_384, blocksPerPixel * factor));
        return new PreviewCamera(clampCenter(worldX(pixelX) - (pixelX - width * 0.5) * scale),
                clampCenter(worldZ(pixelY) - (pixelY - height * 0.5) * scale), scale, width, height);
    }

    private static double clampCenter(double value) { return Math.max(-30_000_000, Math.min(30_000_000, value)); }
}
