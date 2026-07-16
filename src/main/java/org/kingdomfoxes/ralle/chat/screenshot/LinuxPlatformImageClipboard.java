package org.kingdomfoxes.ralle.chat.screenshot;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Publishes image/png through the active Wayland or X11 clipboard protocol helper. */
final class LinuxPlatformImageClipboard implements PlatformImageClipboard {
    enum Backend { WAYLAND, X11 }

    interface CommandRunner {
        Result run(List<String> command, byte[] standardInput) throws IOException, InterruptedException;
    }

    record Result(int exitCode, String output) {}

    private final Backend backend;
    private final CommandRunner runner;

    LinuxPlatformImageClipboard(Backend backend, CommandRunner runner) {
        this.backend = backend;
        this.runner = runner;
    }

    static LinuxPlatformImageClipboard fromEnvironment(Map<String, String> environment) {
        return new LinuxPlatformImageClipboard(detectBackend(environment), new ProcessCommandRunner());
    }

    static Backend detectBackend(Map<String, String> environment) {
        String sessionType = environment.getOrDefault("XDG_SESSION_TYPE", "").toLowerCase(Locale.ROOT);
        if ("wayland".equals(sessionType)) return Backend.WAYLAND;
        if ("x11".equals(sessionType)) return Backend.X11;
        if (present(environment, "WAYLAND_DISPLAY")) return Backend.WAYLAND;
        if (present(environment, "DISPLAY")) return Backend.X11;
        throw new IllegalStateException("No active Wayland or X11 display was detected; open Minecraft in a desktop session and retry");
    }

    @Override
    public void publish(ClipboardImage image) throws IOException {
        List<String> command = backend == Backend.WAYLAND
                ? List.of("wl-copy", "--type", "image/png")
                : List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i");
        try {
            Result result = runner.run(command, image.encodedPngUnsafe());
            if (result.exitCode() != 0) {
                throw new IOException(helperName() + " could not publish the image clipboard"
                        + detail(result.output()) + "; the preview was kept so you can retry");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Linux clipboard copying was interrupted; the preview was kept so you can retry", interrupted);
        } catch (IOException failure) {
            if (isMissingExecutable(failure)) {
                String packageName = backend == Backend.WAYLAND ? "wl-clipboard" : "xclip";
                throw new IOException(helperName() + " is unavailable; install the " + packageName
                        + " package to enable transparent image copying", failure);
            }
            throw failure;
        }
    }

    Backend backend() { return backend; }

    private String helperName() { return backend == Backend.WAYLAND ? "Wayland helper 'wl-copy'" : "X11 helper 'xclip'"; }

    private static boolean present(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }

    private static boolean isMissingExecutable(IOException failure) {
        String message = failure.getMessage();
        return message != null && (message.contains("CreateProcess error=2") || message.toLowerCase(Locale.ROOT).contains("no such file"));
    }

    private static String detail(String output) {
        return output == null || output.isBlank() ? "" : ": " + output.strip();
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public Result run(List<String> command, byte[] standardInput) throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (var input = process.getOutputStream()) {
                input.write(standardInput);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Clipboard helper did not respond within five seconds");
            }
            return new Result(process.exitValue(), "");
        }
    }
}
