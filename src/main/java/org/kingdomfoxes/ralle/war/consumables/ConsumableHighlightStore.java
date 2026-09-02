package org.kingdomfoxes.ralle.war.consumables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Owns the immutable ordered snapshot and publishes only after an atomic save succeeds. */
public final class ConsumableHighlightStore {
    public static final long MAX_IMPORT_BYTES = 1024L * 1024L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumableHighlightStore.class);

    private final Path path;
    private final List<Consumer<List<ConsumableHighlightRule>>> listeners = new CopyOnWriteArrayList<>();
    private volatile List<ConsumableHighlightRule> snapshot;

    public ConsumableHighlightStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.snapshot = loadInitial();
    }

    public List<ConsumableHighlightRule> snapshot() {
        return snapshot;
    }

    public AutoCloseable onChanged(Consumer<List<ConsumableHighlightRule>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    public synchronized void replace(List<ConsumableHighlightRule> rules) throws IOException {
        publishAfterSave(ConsumableHighlightValidation.validate(rules));
    }

    public synchronized void appendImported(List<ConsumableHighlightRule> imported) throws IOException {
        var appended = new ArrayList<>(snapshot);
        appended.addAll(imported);
        publishAfterSave(ConsumableHighlightValidation.validate(appended));
    }

    public void importFile(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        final byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes((int) MAX_IMPORT_BYTES + 1);
        }
        if (bytes.length > MAX_IMPORT_BYTES) throw new IllegalArgumentException("Import exceeds 1 MiB");
        appendImported(ConsumableHighlightJson.decode(new String(bytes, StandardCharsets.UTF_8)));
    }

    public void exportFile(Path destination) throws IOException {
        writeAtomically(destination, ConsumableHighlightJson.encode(snapshot));
    }

    public synchronized void add(ConsumableHighlightRule rule) throws IOException {
        var next = new ArrayList<>(snapshot);
        next.add(rule);
        publishAfterSave(ConsumableHighlightValidation.validate(next));
    }

    public synchronized void addAliases(int ruleIndex, List<String> aliases) throws IOException {
        var next = new ArrayList<>(snapshot);
        var current = next.get(ruleIndex);
        var combined = new ArrayList<>(current.aliases());
        combined.addAll(aliases);
        next.set(ruleIndex, new ConsumableHighlightRule(current.name(), combined, current.style()));
        publishAfterSave(ConsumableHighlightValidation.validate(next));
    }

    public synchronized void updateStyle(int ruleIndex, HighlightStyle style) throws IOException {
        var next = new ArrayList<>(snapshot);
        var current = next.get(ruleIndex);
        next.set(ruleIndex, new ConsumableHighlightRule(current.name(), current.aliases(), style));
        publishAfterSave(ConsumableHighlightValidation.validate(next));
    }

    public synchronized void removeRule(int ruleIndex) throws IOException {
        var next = new ArrayList<>(snapshot);
        next.remove(ruleIndex);
        publishAfterSave(List.copyOf(next));
    }

    public synchronized void removeAlias(int ruleIndex, int aliasIndex) throws IOException {
        var next = new ArrayList<>(snapshot);
        var current = next.get(ruleIndex);
        var aliases = new ArrayList<>(current.aliases());
        aliases.remove(aliasIndex);
        next.set(ruleIndex, new ConsumableHighlightRule(current.name(), aliases, current.style()));
        publishAfterSave(ConsumableHighlightValidation.validate(next));
    }

    public void resetDefaults() throws IOException {
        replace(ConsumableHighlightDefaults.rules());
    }

    private List<ConsumableHighlightRule> loadInitial() {
        if (!Files.exists(path)) {
            var defaults = ConsumableHighlightDefaults.rules();
            try {
                writeAtomically(path, ConsumableHighlightJson.encode(defaults));
            } catch (IOException exception) {
                LOGGER.error("Could not seed consumable highlight rules at {}", path, exception);
            }
            return defaults;
        }
        try {
            return ConsumableHighlightJson.decode(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load consumable highlight rules from {}; defaults remain in memory", path, exception);
            return ConsumableHighlightDefaults.rules();
        }
    }

    private void publishAfterSave(List<ConsumableHighlightRule> next) throws IOException {
        writeAtomically(path, ConsumableHighlightJson.encode(next));
        snapshot = List.copyOf(next);
        for (var listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException exception) {
                LOGGER.error("A consumable highlight listener failed after the saved snapshot was published", exception);
            }
        }
    }

    static void writeAtomically(Path destination, String json) throws IOException {
        Objects.requireNonNull(destination, "destination");
        var parent = destination.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Destination has no parent directory");
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, destination.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }
}
