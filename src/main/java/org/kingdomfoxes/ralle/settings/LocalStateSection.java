package org.kingdomfoxes.ralle.settings;

import org.kingdomfoxes.ralle.platform.AtomicFiles;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/** A feature's storage boundary; production sections share one LocalStateFile. */
public interface LocalStateSection {
    boolean exists();
    String read() throws IOException;
    void write(String text) throws IOException;

    default Properties readProperties() throws IOException {
        var properties = new Properties();
        String text = read();
        if (text == null) return properties;
        try {
            properties.load(new StringReader(text));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid RALLE properties", failure);
        }
        return properties;
    }

    default void writeProperties(Properties properties) throws IOException {
        var writer = new StringWriter();
        properties.store(writer, "RALLE local state");
        write(writer.toString());
    }

    /** Routine UI preferences may coalesce; explicit data edits still use synchronous write(). */
    default CompletableFuture<Void> writePropertiesAsync(Properties properties) {
        try {
            writeProperties(properties);
            return CompletableFuture.completedFuture(null);
        } catch (IOException failure) { return CompletableFuture.failedFuture(failure); }
    }

    /** Standalone data for migration, import/export, and isolated store tests. */
    static LocalStateSection file(Path path) {
        Objects.requireNonNull(path, "path");
        return new LocalStateSection() {
            @Override public boolean exists() { return Files.exists(path); }
            @Override public String read() throws IOException {
                return exists() ? AtomicFiles.read(path, 1024 * 1024) : null;
            }
            @Override public void write(String text) throws IOException { AtomicFiles.write(path, text); }
            @Override public String toString() { return path.toString(); }
        };
    }
}
