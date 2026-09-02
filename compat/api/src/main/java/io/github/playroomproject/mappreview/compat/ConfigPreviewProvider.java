package io.github.playroomproject.mappreview.compat;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.PrioritizedProvider;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import java.util.List;

/**
 * Version-aware configuration bridge, including Tectonic. Preview edits stay in detached snapshots.
 * Applying a change is a separate UI action that validates, backs up and atomically writes the file.
 */
public interface ConfigPreviewProvider extends PrioritizedProvider {
    boolean supportsInstalledVersion(String modVersion, String minecraftVersion);
    List<Field> schema();
    String snapshotJson();
    String validateAndCanonicalize(String editedJson);
    BackendFactory preview(PreviewContext base, String validatedJson);
    void apply(String validatedJson, Fingerprint expectedPreviousConfig);

    record Field(String key, String description, Kind kind, String defaultJson, double minimum, double maximum) { }
    enum Kind { BOOLEAN, INTEGER, DECIMAL, ENUM }
}
