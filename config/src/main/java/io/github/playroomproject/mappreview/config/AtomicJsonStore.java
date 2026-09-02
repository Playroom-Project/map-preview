package io.github.playroomproject.mappreview.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Bounded strict JSON with record validation, atomic replacement and optional explicit-apply backups. */
public final class AtomicJsonStore {
    public static final int MAXIMUM_BYTES = 1_048_576;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().disableHtmlEscaping().create();

    public <T> T read(Path path, Class<T> type) throws IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(MAXIMUM_BYTES + 1); }
        if (bytes.length > MAXIMUM_BYTES) { throw new IOException("Map PreView JSON file exceeds its size limit"); }
        String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            validate(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) { throw new IOException("Trailing JSON content"); }
        }
        try {
            JsonElement element = gson.fromJson(json, JsonElement.class);
            validateRecordShape(element, type);
            return Objects.requireNonNull(gson.fromJson(element, type), "JSON root");
        }
        catch (RuntimeException exception) { throw new IOException("Invalid Map PreView JSON configuration or checkpoint", exception); }
    }

    public void write(Path path, Object value, boolean backupExisting) throws IOException {
        byte[] bytes = (gson.toJson(Objects.requireNonNull(value, "value")) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_BYTES) { throw new IOException("Map PreView JSON output exceeds its size limit"); }
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        if (Files.isSymbolicLink(absolute)) { throw new IOException("Refusing to replace a symbolic-link configuration"); }
        if (backupExisting && Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            byte[] previous;
            try (var stream = Files.newInputStream(absolute)) { previous = stream.readNBytes(MAXIMUM_BYTES + 1); }
            if (previous.length > MAXIMUM_BYTES) { throw new IOException("Existing file exceeds the backup size limit"); }
            replace(absolute.resolveSibling(absolute.getFileName() + ".bak"), previous);
        }
        replace(absolute, bytes);
    }

    private static void replace(Path path, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".map-preview-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) { channel.write(buffer); }
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void validateRecordShape(JsonElement element, Type type) throws IOException {
        if (type instanceof Class<?> record && record.isRecord()) {
            if (element == null || !element.isJsonObject()) { throw new IOException("Expected a JSON record object"); }
            var object = element.getAsJsonObject();
            var fields = new HashSet<String>();
            for (var component : record.getRecordComponents()) {
                fields.add(component.getName());
                if (!object.has(component.getName()) || object.get(component.getName()).isJsonNull()) {
                    throw new IOException("Missing required JSON field: " + component.getName());
                }
                validateRecordShape(object.get(component.getName()), component.getGenericType());
            }
            if (!fields.equals(object.keySet())) { throw new IOException("Unknown JSON fields are not silently discarded"); }
        } else if (type instanceof ParameterizedType parameterized && element != null) {
            if (parameterized.getRawType() == Map.class && element.isJsonObject()) {
                for (var value : element.getAsJsonObject().entrySet()) {
                    validateRecordShape(value.getValue(), parameterized.getActualTypeArguments()[1]);
                }
            } else if (element.isJsonArray()) {
                for (var value : element.getAsJsonArray()) { validateRecordShape(value, parameterized.getActualTypeArguments()[0]); }
            }
        }
    }

    private static void validate(JsonReader reader, int depth) throws IOException {
        if (depth > 32) { throw new IOException("JSON nesting limit exceeded"); }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new HashSet<String>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) { throw new IOException("Duplicate JSON property: " + name); }
                    validate(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) { validate(reader, depth + 1); }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("Unexpected JSON token");
        }
    }
}
