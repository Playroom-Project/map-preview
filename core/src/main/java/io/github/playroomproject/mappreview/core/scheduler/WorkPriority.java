package io.github.playroomproject.mappreview.core.scheduler;

/** All visible coarse tiles precede visible detail; prefetch always follows visible work. */
public record WorkPriority(boolean prefetch, int refinementPass, double distanceSquared) implements Comparable<WorkPriority> {
    public WorkPriority {
        if (refinementPass < 0 || !Double.isFinite(distanceSquared) || distanceSquared < 0) {
            throw new IllegalArgumentException("Invalid tile priority");
        }
    }

    @Override public int compareTo(WorkPriority other) {
        int result = Boolean.compare(prefetch, other.prefetch);
        if (result == 0) { result = Integer.compare(refinementPass, other.refinementPass); }
        if (result == 0) { result = Double.compare(distanceSquared, other.distanceSquared); }
        return result;
    }
}
