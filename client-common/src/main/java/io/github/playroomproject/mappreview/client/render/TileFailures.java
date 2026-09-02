package io.github.playroomproject.mappreview.client.render;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

/** Separates generator failures from normal retirement and bounded-queue backpressure. */
public final class TileFailures {
    private TileFailures() { }

    public static Optional<Throwable> reportable(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof CancellationException || cause instanceof RejectedExecutionException
                ? Optional.empty() : Optional.ofNullable(cause);
    }
}
