package org.kingdomfoxes.ralle.diagnostics;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client-thread command/lifecycle adapter; disk export runs off the render thread. */
public final class RalleDiagnostics {
    private final Minecraft minecraft;
    private final Path directory;
    private DiagnosticProfiler.Session session;
    private Recording recording;
    private Report lastReport;
    private Memory startMemory;
    private Instant started;
    private long startNanos, nextSample, sampledPeakHeap;
    private boolean saving;

    public RalleDiagnostics(Minecraft minecraft, Path gameDirectory) {
        this.minecraft = minecraft;
        directory = gameDirectory.resolve("ralle-diagnostics");
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return literal("diag").executes(command -> status())
                .then(recordingCommand("start", false))
                .then(recordingCommand("jfr", true))
                .then(literal("stop").executes(command -> stop()))
                .then(literal("save").executes(command -> saveLast()));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> recordingCommand(String name, boolean jfr) {
        return literal(name).executes(command -> start(60, jfr))
                .then(argument("seconds", integer(1, 300))
                        .executes(command -> start(getInteger(command, "seconds"), jfr)));
    }

    private int start(int seconds, boolean jfr) {
        if (session != null || saving) { post("ralle.diag.busy"); return 0; }
        Recording candidate = null;
        try {
            if (jfr) {
                // Deliberately omit environment variables, JVM arguments and system properties.
                candidate = new Recording();
                candidate.setName("RALLE diagnostics");
                candidate.setMaxSize(64L * 1024 * 1024);
                candidate.setMaxAge(Duration.ofSeconds(seconds));
                candidate.setDuration(Duration.ofSeconds(seconds));
                candidate.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
                candidate.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
                candidate.enable("jdk.ObjectAllocationSample").with("throttle", "150/s").withStackTrace();
                candidate.enable("jdk.GarbageCollection");
                candidate.enable("jdk.GCHeapSummary");
                candidate.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(10)).withStackTrace();
                candidate.start();
            }
            startMemory = memory();
            sampledPeakHeap = startMemory.heapUsedBytes();
            started = Instant.now();
            startNanos = System.nanoTime();
            nextSample = startNanos + 1_000_000_000L;
            session = DiagnosticProfiler.start(seconds, allocationCounter());
            recording = candidate;
            post("ralle.diag.started", seconds, jfr ? "JFR + sections" : "sections");
            return 1;
        } catch (RuntimeException | LinkageError failure) {
            if (candidate != null) candidate.close();
            post("ralle.diag.failed", failure.getClass().getSimpleName());
            return 0;
        }
    }

    /** No JVM management calls or clock reads while idle. Pausing cannot extend scoped collection. */
    public void tick() {
        if (session == null) return;
        if (session.expired()) { stop(); return; }
        long now = System.nanoTime();
        if (now >= nextSample) {
            sampledPeakHeap = Math.max(sampledPeakHeap, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            nextSample = now + 1_000_000_000L;
        }
    }

    private int stop() {
        if (session == null) { post("ralle.diag.idle"); return 0; }
        DiagnosticProfiler.stop(session);
        var end = memory();
        var version = FabricLoader.getInstance().getModContainer("ralle").orElseThrow().getMetadata().getVersion().getFriendlyString();
        lastReport = new Report(1, version, System.getProperty("java.version"), started.toString(),
                (System.nanoTime() - startNanos) / 1_000_000_000d,
                "Inclusive wall time and same-thread allocation in instrumented sections; nested sections overlap. "
                        + "Allocation is churn, not retained memory. Heap/GC/direct buffers cover the entire JVM. "
                        + "Sampled peak is not an exact high-water mark. GPU/native memory and asynchronous work are not attributed. "
                        + "Profiler overhead is included; zero calls means that section was not exercised.",
                startMemory, end, Math.max(sampledPeakHeap, end.heapUsedBytes()), session.snapshot());
        session = null;
        var finishedRecording = recording;
        recording = null;
        save(lastReport, finishedRecording);
        return 1;
    }

    private int status() {
        var current = memory();
        post("ralle.diag.status", session == null ? "idle" : "recording",
                current.heapUsedBytes() / 1048576, current.heapMaxBytes() / 1048576);
        post("ralle.diag.help");
        if (lastReport != null) {
            lastReport.sections().stream().filter(s -> s.calls() > 0)
                    .sorted(java.util.Comparator.comparingLong(DiagnosticProfiler.Measurement::totalNanos).reversed())
                    .limit(3).forEach(s -> post("ralle.diag.section", s.section(), s.calls(),
                            String.format(java.util.Locale.ROOT, "%.3f", s.totalNanos() / 1_000_000d),
                            s.allocationSamples() == 0 ? "unavailable" : Long.toString(s.allocatedBytes() / 1024)));
        }
        return 1;
    }

    private int saveLast() {
        if (lastReport == null) { post("ralle.diag.idle"); return 0; }
        if (saving || session != null) { post("ralle.diag.busy"); return 0; }
        save(lastReport, null);
        return 1;
    }

    private void save(Report report, Recording finishedRecording) {
        saving = true;
        CompletableFuture.runAsync(() -> {
            try (finishedRecording) {
                Files.createDirectories(directory);
                if (finishedRecording != null) {
                    if (finishedRecording.getState() == jdk.jfr.RecordingState.RUNNING) finishedRecording.stop();
                    var temporary = directory.resolve("latest.jfr.tmp");
                    finishedRecording.dump(temporary);
                    replace(temporary, directory.resolve("latest.jfr"));
                }
                var temporary = directory.resolve("latest.json.tmp");
                Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                replace(temporary, directory.resolve("latest.json"));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }).whenComplete((ignored, failure) -> minecraft.execute(() -> {
            saving = false;
            if (failure == null) post("ralle.diag.saved", directory.resolve("latest.json").toString(),
                    finishedRecording == null ? "" : " + latest.jfr");
            else post("ralle.diag.save-failed");
        }));
    }

    private static void replace(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static java.util.function.LongSupplier allocationCounter() {
        // Do not change JVM-global tracking settings owned by other profilers.
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean extended
                && extended.isThreadAllocatedMemorySupported() && extended.isThreadAllocatedMemoryEnabled()) {
            return () -> {
                try { return extended.getCurrentThreadAllocatedBytes(); }
                catch (UnsupportedOperationException | SecurityException unavailable) { return -1L; }
            };
        }
        return () -> -1L;
    }

    private static Memory memory() {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long gcCount = 0, gcMillis = 0, direct = 0;
        boolean countKnown = true, timeKnown = true;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            countKnown &= gc.getCollectionCount() >= 0;
            timeKnown &= gc.getCollectionTime() >= 0;
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        for (var pool : ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)) {
            if (pool.getName().equals("direct")) direct = pool.getMemoryUsed();
        }
        return new Memory(heap.getUsed(), heap.getCommitted(), heap.getMax(), nonHeap.getUsed(), direct,
                countKnown ? gcCount : -1, timeKnown ? gcMillis : -1);
    }

    /** Shutdown discards an unfinished run and releases only the recording owned by RALLE. */
    public void close() {
        if (session != null) { DiagnosticProfiler.stop(session); session = null; }
        if (recording != null) { recording.close(); recording = null; }
    }

    private void post(String key, Object... values) { RalleChatMessages.post(minecraft, Component.translatable(key, values)); }

    private record Memory(long heapUsedBytes, long heapCommittedBytes, long heapMaxBytes,
                          long nonHeapUsedBytes, long directBufferBytes, long gcCollections, long gcMillis) {}
    private record Report(int schemaVersion, String modVersion, String javaVersion, String startedUtc,
                          double elapsedSeconds, String interpretation, Memory start, Memory end,
                          long sampledPeakHeapBytes, List<DiagnosticProfiler.Measurement> sections) {}
}
