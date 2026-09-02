package io.github.playroomproject.mappreview.core.tile;

/** Independent lazy channels. Display styles such as contours do not create worldgen channels. */
public enum DataLayer {
    BIOMES(true, 0), HEIGHT(false, 1), SURFACE(false, 0), CAVE_BIOMES(true, 0),
    CAVE_DENSITY(true, 0), CAVE_BLOCKS(true, 0), SLIME_CHUNKS(false, 0),
    STRUCTURE_CANDIDATES(false, 0), VERIFIED_STRUCTURES(false, 0);

    private final boolean usesY;
    private final int border;

    DataLayer(boolean usesY, int border) { this.usesY = usesY; this.border = border; }
    public boolean usesY() { return usesY; }
    public int border() { return border; }
    public boolean structures() { return this == STRUCTURE_CANDIDATES || this == VERIFIED_STRUCTURES; }
}
