package io.github.playroomproject.mappreview.client.render;

import java.util.ArrayDeque;
import java.util.Objects;

/** Bounded CPU-to-render handoff. Epoch and display revision are checked again at upload time. */
public final class RenderUploadQueue {
    private final Thread renderThread = Thread.currentThread();
    private final ArrayDeque<Upload> queue = new ArrayDeque<>();
    private final int maximumTiles;
    private final long maximumBytes;
    private long bytes;
    private long epoch;
    private long displayRevision;

    public RenderUploadQueue(int maximumTiles, long maximumBytes) {
        if (maximumTiles < 1 || maximumBytes < 1) { throw new IllegalArgumentException("Invalid render upload budget"); }
        this.maximumTiles = maximumTiles;
        this.maximumBytes = maximumBytes;
    }

    /** Called on the render thread before accepting a new seed, dimension or display style. */
    public synchronized void activate(long newEpoch, long newDisplayRevision) {
        assertRenderThread();
        epoch = newEpoch;
        displayRevision = newDisplayRevision;
        queue.clear();
        bytes = 0;
    }

    public synchronized boolean offer(long tileEpoch, long revision, ColoredTile tile) {
        Objects.requireNonNull(tile, "tile");
        if (tileEpoch != epoch || revision != displayRevision || queue.size() >= maximumTiles
                || tile.byteSize() > maximumBytes - bytes) { return false; }
        queue.addLast(new Upload(tileEpoch, revision, tile));
        bytes += tile.byteSize();
        return true;
    }

    public int drain(PreviewRenderer renderer, int tileBudget) {
        assertRenderThread();
        if (tileBudget < 0) { throw new IllegalArgumentException("Negative frame upload budget"); }
        int count = 0;
        while (count < tileBudget) {
            Upload upload;
            synchronized (this) {
                upload = queue.pollFirst();
                if (upload == null) { break; }
                bytes -= upload.tile().byteSize();
                if (upload.epoch() != epoch || upload.revision() != displayRevision) { continue; }
            }
            renderer.upload(upload.tile());
            count++;
        }
        return count;
    }

    public synchronized long bytes() { return bytes; }
    public synchronized int size() { return queue.size(); }
    private void assertRenderThread() {
        if (Thread.currentThread() != renderThread) { throw new IllegalStateException("Map PreView GPU operations require the render thread"); }
    }
    private record Upload(long epoch, long revision, ColoredTile tile) { }
}
