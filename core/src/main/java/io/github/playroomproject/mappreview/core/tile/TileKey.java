package io.github.playroomproject.mappreview.core.tile;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.Objects;

/** Includes the context and backend data revision, geometry, channel, Y and query settings. */
public record TileKey(Fingerprint sessionFingerprint, TileRequest request) {
    public TileKey {
        Objects.requireNonNull(sessionFingerprint, "sessionFingerprint");
        Objects.requireNonNull(request, "request");
    }

    public long byteSize() {
        long bytes = 384;
        for (var id : request.structures().ids()) { bytes += 96L + 2L * id.value().length(); }
        return bytes;
    }
}
