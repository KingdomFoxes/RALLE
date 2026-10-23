package org.kingdomfoxes.ralle.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Save in the destination directory and publish only after a complete, flushed write. */
public final class AtomicFiles {
    private AtomicFiles() {}

    public static void write(Path destination, String text) throws IOException {
        var target = destination.toAbsolutePath();
        var parent = target.getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, "ralle-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // Fail without replacing the original when atomic moves are unsupported.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String read(Path path, int maximumBytes) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) throw new IOException("RALLE file exceeds " + maximumBytes + " bytes: " + path);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
