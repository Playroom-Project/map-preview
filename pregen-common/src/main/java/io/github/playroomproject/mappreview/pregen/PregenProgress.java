package io.github.playroomproject.mappreview.pregen;

public record PregenProgress(PregenState state, long totalChunks, long completedChunks, long failedAttempts,
                             int inFlight, double currentChunksPerSecond, double averageChunksPerSecond,
                             long elapsedNanos, String lastFailure) {
    public double fraction() { return totalChunks == 0 ? 0 : (double) completedChunks / totalChunks; }
    public double estimatedSecondsRemaining() {
        return averageChunksPerSecond > 0 ? (totalChunks - completedChunks) / averageChunksPerSecond : Double.POSITIVE_INFINITY;
    }
}
