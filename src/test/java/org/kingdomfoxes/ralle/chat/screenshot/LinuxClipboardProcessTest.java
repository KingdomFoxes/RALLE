package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LinuxClipboardProcessTest {
    @TempDir Path directory;

    @Test void sendsTheEntireImageBeforeTheHelperExits() throws Exception {
        var runner = new LinuxPlatformImageClipboard.ProcessCommandRunner();
        assertEquals(0, runner.run(command("read"), new byte[2 * 1024 * 1024]).exitCode());
    }

    @Test void aHelperWhichNeverReadsStdinCannotHoldTheCaptureForever() throws Exception {
        var runner = new LinuxPlatformImageClipboard.ProcessCommandRunner();
        assertTimeoutPreemptively(Duration.ofSeconds(12), () -> {
            var failure = assertThrows(IOException.class,
                    () -> runner.run(command("stall"), new byte[2 * 1024 * 1024]));
            assertTrue(failure.getMessage().contains("five seconds"));
        });
        long pid = Long.parseLong(Files.readString(directory.resolve("pid")));
        var process = ProcessHandle.of(pid);
        if (process.isPresent()) process.get().onExit().get(3, TimeUnit.SECONDS);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    private List<String> command(String mode) throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var classes = Path.of(Helper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(java, "-cp", classes, Helper.class.getName(), mode, directory.resolve("pid").toString());
    }

    public static class Helper {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
            if (args[0].equals("stall")) Thread.sleep(30_000);
            else if (System.in.readAllBytes().length != 2 * 1024 * 1024) System.exit(1);
        }
    }
}
