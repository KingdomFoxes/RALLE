package org.kingdomfoxes.ralle.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kingdomfoxes.ralle.platform.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;

/** Bounded best-effort diagnostics, independent of the identity/state writer and switch timing. */
final class ClassCaptureWriter implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService writer = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), task -> {
                var thread = new Thread(task, "RALLE class captures");
                thread.setDaemon(true);
                return thread;
            });

    /** Caller transfers a detached JSON tree; no menu, item, registry or Minecraft object is retained. */
    boolean submit(Path destination, JsonObject snapshot) {
        try {
            writer.execute(() -> {
                try {
                    String json = JSON.toJson(snapshot);
                    if (json.getBytes(StandardCharsets.UTF_8).length > ClassMenuEvidence.MAX_BYTES)
                        throw new IOException("Class menu capture exceeds the diagnostic byte limit");
                    AtomicFiles.write(destination, json);
                } catch (IOException | RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(ClassCaptureWriter.class).warn("Could not save class menu evidence", failure);
                }
            });
            return true;
        } catch (RejectedExecutionException full) { return false; }
    }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException failure) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
