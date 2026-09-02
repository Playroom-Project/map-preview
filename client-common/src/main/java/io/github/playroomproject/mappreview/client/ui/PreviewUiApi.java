package io.github.playroomproject.mappreview.client.ui;

import io.github.playroomproject.mappreview.core.api.ResourceId;

/** Stable actions for screens and optional FancyMenu bridges; no FancyMenu classes enter the core. */
public interface PreviewUiApi {
    void open();
    void setSeed(String seedText);
    void randomizeSeed();
    void setDimension(ResourceId dimension);
    void createWorld();
    void openPregen();
    PreviewUiState state();
}
