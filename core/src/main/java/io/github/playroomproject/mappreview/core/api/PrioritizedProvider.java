package io.github.playroomproject.mappreview.core.api;

/** Stable provider identity and deterministic selection order. Higher priorities run first. */
public interface PrioritizedProvider {
    ResourceId id();
    default int priority() { return 0; }
}
