package io.github.playroomproject.mappreview.core.filter;

/** Pure display operation. The core never calls world generation from a filter. */
public interface PreviewFilter {
    PreviewFilter IDENTITY = new PreviewFilter() { };
    default boolean needsBiomes() { return false; }
    default boolean needsHeights() { return false; }
    default int color(int biomeId, int height, double slopeDegrees, int originalArgb) { return originalArgb; }
}
