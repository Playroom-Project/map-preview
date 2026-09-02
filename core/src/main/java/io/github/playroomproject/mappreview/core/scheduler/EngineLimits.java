package io.github.playroomproject.mappreview.core.scheduler;

/** The task limit includes running work, pending work and obsolete work still draining. */
public record EngineLimits(int workers, int maximumOutstandingTasks, long cacheBytes) {
    public EngineLimits {
        if (workers < 1 || workers > 64 || maximumOutstandingTasks < workers
                || maximumOutstandingTasks > 16_384 || cacheBytes < 0) {
            throw new IllegalArgumentException("Invalid Map PreView engine limits");
        }
    }
}
