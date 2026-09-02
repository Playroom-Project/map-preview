package io.github.playroomproject.mappreview.core.cache;

import io.github.playroomproject.mappreview.core.tile.TileData;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import java.util.LinkedHashMap;

/** A byte-budgeted access-order LRU. Oversize entries never evict useful resident data. */
public final class TileCache {
    private final LinkedHashMap<TileKey, TileData> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long maximumBytes;
    private long bytes;
    private long hits;
    private long misses;
    private long evictions;

    public TileCache(long maximumBytes) {
        if (maximumBytes < 0) { throw new IllegalArgumentException("Negative cache budget"); }
        this.maximumBytes = maximumBytes;
    }

    public synchronized TileData get(TileKey key) {
        TileData data = entries.get(key);
        if (data == null) { misses++; } else { hits++; }
        return data;
    }

    public synchronized boolean put(TileData data) {
        long weight = data.byteSize();
        if (weight <= 0 || weight > maximumBytes) { return false; }
        TileData previous = entries.remove(data.key());
        if (previous != null) { bytes -= previous.byteSize(); }
        evictTo(maximumBytes - weight);
        entries.put(data.key(), data);
        bytes += weight;
        return true;
    }

    public synchronized void resize(long budget) {
        if (budget < 0) { throw new IllegalArgumentException("Negative cache budget"); }
        maximumBytes = budget;
        evictTo(budget);
    }

    public synchronized void clear() { entries.clear(); bytes = 0; }
    public synchronized Stats stats() { return new Stats(entries.size(), bytes, maximumBytes, hits, misses, evictions); }

    private void evictTo(long target) {
        var iterator = entries.entrySet().iterator();
        while (bytes > target && iterator.hasNext()) {
            bytes -= iterator.next().getValue().byteSize();
            iterator.remove();
            evictions++;
        }
    }

    public record Stats(int entries, long bytes, long maximumBytes, long hits, long misses, long evictions) { }
}
