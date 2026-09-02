package io.github.playroomproject.mappreview.pregen;

public record PregenSettings(int maximumInFlight, int maximumRetries) {
    public PregenSettings {
        if (maximumInFlight < 1 || maximumInFlight > 256 || maximumRetries < 0 || maximumRetries > 8) {
            throw new IllegalArgumentException("Invalid pregeneration limits");
        }
    }
}
