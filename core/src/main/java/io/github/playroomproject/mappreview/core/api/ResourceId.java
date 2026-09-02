package io.github.playroomproject.mappreview.core.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** A stable registry identity, independent of Minecraft mappings and numeric registry IDs. */
public record ResourceId(String value) implements Comparable<ResourceId> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ResourceId {
        Objects.requireNonNull(value, "value");
        if (value.length() > 512 || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid namespaced ID: " + value);
        }
    }

    @Override public int compareTo(ResourceId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
