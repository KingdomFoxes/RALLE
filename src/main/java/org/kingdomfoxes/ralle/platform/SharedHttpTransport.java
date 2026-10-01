package org.kingdomfoxes.ralle.platform;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Client-lifetime transport only; credentials remain on individual requests. */
public final class SharedHttpTransport implements Supplier<HttpClient>, AutoCloseable {
    private static final SharedHttpTransport SHARED = new SharedHttpTransport(() ->
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());

    private final Supplier<HttpClient> factory;
    private HttpClient client;
    private boolean closed;

    public SharedHttpTransport(Supplier<HttpClient> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public static SharedHttpTransport shared() { return SHARED; }

    @Override
    public synchronized HttpClient get() {
        if (closed) throw new IllegalStateException("RALLE HTTP transport is shut down");
        if (client == null) client = Objects.requireNonNull(factory.get(), "client");
        return client;
    }

    /** Shutdown must not wait for an open live socket or initialize an unused client. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (client != null) client.shutdownNow();
    }
}
