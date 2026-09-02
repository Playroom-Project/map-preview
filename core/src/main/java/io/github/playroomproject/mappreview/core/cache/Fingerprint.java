package io.github.playroomproject.mappreview.core.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** SHA-256 over length-prefixed fields; field boundaries and collection ordering are explicit. */
public record Fingerprint(String hex) {
    public Fingerprint {
        Objects.requireNonNull(hex, "hex");
        if (!hex.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid SHA-256 fingerprint"); }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final MessageDigest digest;
        private boolean finished;

        private Builder() {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
        }

        public Builder add(String value) {
            if (finished) { throw new IllegalStateException("Fingerprint already finalized"); }
            byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
            digest.update(bytes);
            return this;
        }

        public Builder add(long value) { return add(Long.toString(value)); }

        public Builder addSorted(Map<String, String> fields) {
            add(fields.size());
            new TreeMap<>(fields).forEach((key, value) -> { add(key); add(value); });
            return this;
        }

        public Fingerprint finish() {
            if (finished) { throw new IllegalStateException("Fingerprint already finalized"); }
            finished = true;
            return new Fingerprint(HexFormat.of().formatHex(digest.digest()));
        }
    }
}
