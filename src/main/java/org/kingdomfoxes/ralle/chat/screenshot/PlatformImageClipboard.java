package org.kingdomfoxes.ralle.chat.screenshot;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Headless-safe publication of PNG and, where supported, the platform's native image flavor. */
public interface PlatformImageClipboard {
    void publish(ClipboardImage image) throws IOException;

    static PlatformImageClipboard systemDefault() {
        PlatformImageClipboard selected = PlatformImageClipboards.select(
                System.getProperty("os.name", "unknown"), System.getenv()
        );
        return new RetryingPlatformImageClipboard(selected, 3, 50L, Thread::sleep);
    }
}

final class PlatformImageClipboards {
    private PlatformImageClipboards() {}

    static PlatformImageClipboard select(String osName, Map<String, String> environment) {
        String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mac") || normalized.contains("darwin")) return new MacPlatformImageClipboard();
        if (normalized.contains("win")) return new WindowsPlatformImageClipboard();
        if (normalized.contains("linux")) return LinuxPlatformImageClipboard.fromEnvironment(environment);
        return image -> { throw new IOException("Image clipboard copying is not supported on " + osName); };
    }
}

class ClipboardBusyException extends IOException {
    ClipboardBusyException(String message) { super(message); }
}

final class RetryingPlatformImageClipboard implements PlatformImageClipboard {
    interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }

    private final PlatformImageClipboard delegate;
    private final int maximumAttempts;
    private final long delayMillis;
    private final Sleeper sleeper;

    RetryingPlatformImageClipboard(PlatformImageClipboard delegate, int maximumAttempts, long delayMillis, Sleeper sleeper) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        this.delegate = delegate;
        this.maximumAttempts = maximumAttempts;
        this.delayMillis = Math.max(0L, delayMillis);
        this.sleeper = sleeper;
    }

    @Override
    public void publish(ClipboardImage image) throws IOException {
        ClipboardBusyException lastBusy = null;
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                delegate.publish(image);
                return;
            } catch (ClipboardBusyException busy) {
                lastBusy = busy;
                if (attempt == maximumAttempts) break;
                try {
                    sleeper.sleep(delayMillis * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Clipboard copying was interrupted; the preview was kept so you can retry", interrupted);
                }
            }
        }
        throw new IOException("The clipboard stayed busy after " + maximumAttempts
                + " attempts; close other clipboard tools and retry", lastBusy);
    }
}
