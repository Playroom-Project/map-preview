package io.github.playroomproject.mappreview.core;

/** Stable runtime identity. The distribution title belongs only in release metadata. */
public final class MapPreView {
    public static final String NAME = "Map PreView";
    public static final String ID = "map_preview";
    public static final int CACHE_FORMAT_VERSION = 1;
    public static final System.Logger LOGGER = System.getLogger(NAME);

    private MapPreView() { }
}
