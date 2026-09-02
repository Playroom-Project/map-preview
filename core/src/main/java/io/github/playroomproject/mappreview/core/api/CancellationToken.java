package io.github.playroomproject.mappreview.core.api;

import java.util.concurrent.CancellationException;

/** Cooperative cancellation; adapters must not interrupt arbitrary world generation code. */
@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancelled();

    default void check() {
        if (isCancelled()) {
            throw new CancellationException("Map PreView work is no longer needed");
        }
    }
}
