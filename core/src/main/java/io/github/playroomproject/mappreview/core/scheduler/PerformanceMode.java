package io.github.playroomproject.mappreview.core.scheduler;

/** Conservative defaults reserve resources for the game; adapters may impose tighter limits. */
public enum PerformanceMode {
    ECO, BALANCED, FAST, MAX;

    public int workers(HardwareProfile hardware) {
        int cpus = hardware.logicalProcessors();
        return Math.max(1, switch (this) {
            case ECO -> Math.min(4, cpus / 4);
            case BALANCED -> Math.min(8, (cpus - 1) / 2);
            case FAST -> Math.min(32, cpus - 2);
            case MAX -> Math.min(64, cpus);
        });
    }
}
