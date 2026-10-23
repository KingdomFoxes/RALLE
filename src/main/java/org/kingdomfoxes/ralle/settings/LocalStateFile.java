package org.kingdomfoxes.ralle.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.kingdomfoxes.ralle.platform.AtomicFiles;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

/** One versioned, synchronized local file. Failed saves never publish a partial section. */
public final class LocalStateFile implements AutoCloseable {
    public static final String FILE_NAME = "ralle.json";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<Legacy> LEGACY = List.of(
            new Legacy("settings", "ralle.properties"),
            new Legacy("hud", "ralle-hud-layout.properties"),
            new Legacy("navigation", "ralle-settings-ui.properties"),
            new Legacy("onboarding", "ralle-onboarding.properties"),
            new Legacy("requeue", "ralle-auto-requeue.properties"),
            new Legacy("classes", "ralle-class-shortcuts.json"),
            new Legacy("consumables", "ralle-consumable-highlights.json"),
            new Legacy("guildRanks", "ralle-ranks.json"));

    private final Path path;
    @FunctionalInterface interface DiskWrite { void write(Path path, String json) throws IOException; }
    private final DiskWrite diskWrite;
    private volatile JsonObject root;
    private IOException loadFailure;
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private List<Pending> writing = List.of();
    private boolean draining, closed;
    private volatile Thread worker;
    // Only one drain task is scheduled, with at most one pending value/future per section.
    // Idle threads expire, including isolated stores created by tests/tooling.
    private final ExecutorService writer = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                var thread = new Thread(task, "RALLE local state");
                thread.setDaemon(true);
                worker = thread;
                return thread;
            });

    public LocalStateFile(Path path) {
        this(path, AtomicFiles::write);
    }

    LocalStateFile(Path path, DiskWrite diskWrite) {
        this.path = path;
        this.diskWrite = diskWrite;
        root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        try {
            if (Files.exists(path)) {
                root = JsonParser.parseString(AtomicFiles.read(path, MAX_BYTES)).getAsJsonObject();
                if (!root.has("schemaVersion") || !root.get("schemaVersion").equals(new com.google.gson.JsonPrimitive(1)))
                    throw new IOException("Unsupported RALLE local state schema");
                if (root.has("migrationFailures")) {
                    if (!root.get("migrationFailures").isJsonObject()) throw new IOException("Invalid migration recovery data");
                    retryFailedMigrations();
                }
            } else {
                migrate();
            }
        } catch (IOException | RuntimeException failure) {
            loadFailure = new IOException("Could not load RALLE local state; preserving " + path, failure);
            LoggerFactory.getLogger(LocalStateFile.class).error(loadFailure.getMessage(), failure);
        }
    }

    public LocalStateSection section(String name) {
        if (LEGACY.stream().noneMatch(value -> value.section.equals(name)))
            throw new IllegalArgumentException("Unknown local state section: " + name);
        return new LocalStateSection() {
            @Override public boolean exists() {
                synchronized (LocalStateFile.this) {
                    return loadFailure != null || root.has(name) || migrationFailed(name);
                }
            }
            @Override public String read() throws IOException {
                synchronized (LocalStateFile.this) {
                    checkReadable(name);
                    return root.has(name) ? JSON.toJson(root.get(name)) : null;
                }
            }
            @Override public void write(String text) throws IOException {
                final JsonElement value;
                try { value = JsonParser.parseString(text); }
                catch (RuntimeException failure) { throw new IOException("Invalid JSON for " + name, failure); }
                await(enqueue(name, value));
            }
            @Override public Properties readProperties() throws IOException {
                synchronized (LocalStateFile.this) {
                    checkReadable(name);
                    var properties = new Properties();
                    if (!root.has(name)) return properties;
                    try {
                        for (var entry : root.getAsJsonObject(name).entrySet()) {
                            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                                throw new IllegalArgumentException("Expected a string property");
                            properties.setProperty(entry.getKey(), entry.getValue().getAsString());
                        }
                    } catch (RuntimeException failure) { throw new IOException("Invalid RALLE section: " + name, failure); }
                    return properties;
                }
            }
            @Override public void writeProperties(Properties properties) throws IOException {
                await(enqueue(name, propertiesJson(properties)));
            }
            @Override public CompletableFuture<Void> writePropertiesAsync(Properties properties) {
                return enqueue(name, propertiesJson(properties));
            }
            @Override public String toString() { return path + "#" + name; }
        };
    }

    private synchronized CompletableFuture<Void> enqueue(String name, JsonElement value) {
        try { checkReadable(name); }
        catch (IOException failure) { return CompletableFuture.failedFuture(failure); }
        if (closed) return CompletableFuture.failedFuture(new IOException("RALLE local state is closed"));
        var update = pending.get(name);
        if (update == null) {
            update = new Pending(name, value);
            pending.put(name, update);
        } else {
            update.value = value;
        }
        if (!draining) {
            draining = true;
            writer.execute(this::drain);
        }
        return update.saved;
    }

    private void drain() {
        while (true) {
            final List<Pending> batch;
            synchronized (this) {
                if (pending.isEmpty()) { draining = false; return; }
                batch = new ArrayList<>(pending.values());
                pending.clear();
                writing = batch;
            }
            try {
                var next = new JsonObject();
                root.entrySet().forEach(entry -> next.add(entry.getKey(), entry.getValue()));
                for (var update : batch) next.add(update.name, update.value);
                if (!next.equals(root)) {
                    save(next);
                    root = next;
                }
                batch.forEach(update -> update.saved.complete(null));
            } catch (IOException | RuntimeException failure) {
                batch.forEach(update -> update.saved.completeExceptionally(failure));
            } finally {
                synchronized (this) { writing = List.of(); }
            }
        }
    }

    /** Wait only at explicit durability boundaries, never from a UI tick or render callback. */
    public void flush() throws IOException {
        final CompletableFuture<Void> barrier;
        synchronized (this) {
            var saves = new ArrayList<CompletableFuture<Void>>();
            writing.forEach(update -> saves.add(update.saved));
            pending.values().forEach(update -> saves.add(update.saved));
            barrier = CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
        }
        await(barrier, true);
    }

    private void await(CompletableFuture<Void> saved) throws IOException {
        await(saved, false);
    }

    private void await(CompletableFuture<Void> saved, boolean bounded) throws IOException {
        if (Thread.currentThread() == worker && !saved.isDone())
            throw new IOException("Cannot block the local-state writer from a completion callback");
        try {
            if (bounded) saved.get(5, TimeUnit.SECONDS);
            else saved.get();
        }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while saving RALLE local state", failure);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("Could not save RALLE local state", failure.getCause());
        } catch (TimeoutException failure) { throw new IOException("Timed out saving RALLE local state", failure); }
    }

    @Override public void close() throws IOException {
        synchronized (this) { closed = true; }
        try { flush(); }
        finally { writer.shutdown(); }
    }

    private void checkReadable(String name) throws IOException {
        if (loadFailure != null) throw loadFailure;
        if (migrationFailed(name))
            throw new IOException("Legacy RALLE section could not be migrated: " + name
                    + "; original file is preserved. Repair the original file and restart.");
    }

    private boolean migrationFailed(String name) {
        return root.has("migrationFailures") && root.getAsJsonObject("migrationFailures").has(name);
    }

    private void retryFailedMigrations() throws IOException {
        var failures = root.getAsJsonObject("migrationFailures");
        boolean changed = false;
        for (var legacy : LEGACY) {
            if (!failures.has(legacy.section)) continue;
            // Never replace a section which was explicitly repaired in the unified file.
            if (!root.has(legacy.section)) {
                var source = LocalStateSection.file(path.resolveSibling(legacy.file));
                if (!source.exists()) continue;
                try { root.add(legacy.section, readLegacy(legacy, source)); }
                catch (IOException | RuntimeException failure) { continue; }
            }
            failures.remove(legacy.section);
            changed = true;
        }
        if (failures.isEmpty()) root.remove("migrationFailures");
        if (changed) save(root);
    }

    private void save(JsonObject next) throws IOException {
        String json = JSON.toJson(next) + "\n";
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("RALLE local state exceeds 8 MiB");
        diskWrite.write(path, json);
    }

    private void migrate() throws IOException {
        boolean found = false;
        for (var legacy : LEGACY) {
            var source = LocalStateSection.file(path.resolveSibling(legacy.file));
            if (!source.exists()) continue;
            try {
                root.add(legacy.section, readLegacy(legacy, source));
            } catch (IOException | RuntimeException failure) {
                if (!root.has("migrationFailures")) root.add("migrationFailures", new JsonObject());
                root.getAsJsonObject("migrationFailures").addProperty(legacy.section, legacy.file);
                LoggerFactory.getLogger(LocalStateFile.class).warn("Preserving unreadable legacy RALLE file {}", source, failure);
            }
            found = true;
        }
        // Original files are untouched recovery copies; an existing unified file is always authoritative.
        if (found) save(root);
    }

    private static JsonElement readLegacy(Legacy legacy, LocalStateSection source) throws IOException {
        return legacy.file.endsWith(".properties")
                ? propertiesJson(source.readProperties()) : JsonParser.parseString(source.read());
    }

    private static JsonObject propertiesJson(Properties properties) {
        var result = new JsonObject();
        properties.stringPropertyNames().stream().sorted()
                .forEach(key -> result.addProperty(key, properties.getProperty(key)));
        return result;
    }

    private record Legacy(String section, String file) {}

    private static final class Pending {
        private final String name;
        private JsonElement value;
        private final CompletableFuture<Void> saved = new CompletableFuture<>();
        private Pending(String name, JsonElement value) { this.name = name; this.value = value; }
    }
}
