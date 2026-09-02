package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.ResourceId;

/**
 * Creates worker-confined samplers. Parallelism is opt-in: unknown modded generators are serialized.
 * Opening, sampling and closing happen on the same worker under the session concurrency limit.
 */
public interface BackendFactory {
    ResourceId id();
    BackendCapabilities capabilities(PreviewContext context);
    WorldgenSampler open(PreviewContext context);
    default int maximumConcurrency() { return 1; }
    default int dataVersion() { return 1; }
}
