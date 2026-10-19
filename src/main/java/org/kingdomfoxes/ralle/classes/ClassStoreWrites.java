package org.kingdomfoxes.ralle.classes;

import java.io.IOException;
import java.util.concurrent.*;

/** One bounded writer orders load observations with synchronous edits and evidence migrations. */
final class ClassStoreWrites {
    @FunctionalInterface interface Write { void run() throws IOException; }
    private static volatile Thread worker;
    private static final ExecutorService WRITER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), task -> {
                var thread = new Thread(task, "RALLE class storage");
                thread.setDaemon(true);
                worker = thread;
                return thread;
            });

    private ClassStoreWrites() { }
    static boolean onWriter() { return Thread.currentThread() == worker; }

    static CompletableFuture<Void> submit(Write write) {
        var result = new CompletableFuture<Void>();
        try {
            WRITER.execute(() -> {
                try { write.run(); result.complete(null); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException full) { result.completeExceptionally(full); }
        return result;
    }

    /** Explicit edits retain save-before-publish and synchronous error reporting. */
    static void run(Write write) throws IOException {
        if (onWriter()) { write.run(); return; }
        try { submit(write).get(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while saving class data", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IOException("Could not save class data", failure.getCause());
        }
    }

    static void flush() throws IOException {
        if (onWriter()) return;
        try { submit(() -> {}).get(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while flushing class data", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Could not flush pending class data", failure);
        }
    }
}
