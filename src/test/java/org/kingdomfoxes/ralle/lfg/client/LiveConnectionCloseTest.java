package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiveConnectionCloseTest {
    @Test void unresponsiveOrFailedPeersCannotRetainSupersededSockets() throws Exception {
        for (boolean throwsOnClose : new boolean[] {false, true}) {
            var closes = new AtomicInteger();
            var aborted = new CountDownLatch(1);
            var socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),
                    new Class<?>[] {WebSocket.class}, (proxy, method, args) -> {
                        if (method.getName().equals("sendClose")) {
                            closes.incrementAndGet();
                            if (throwsOnClose) throw new IllegalStateException("Already closed");
                            return new CompletableFuture<>(); // Peer never acknowledges the close.
                        }
                        if (method.getName().equals("abort")) { aborted.countDown(); return null; }
                        throw new AssertionError("Unexpected socket operation: " + method.getName());
                    });
            var connection = HttpLfgGateway.closingConnection(socket);
            connection.close();
            connection.close();
            assertTrue(aborted.await(5, TimeUnit.SECONDS), "Transport must be released without a peer reply");
            assertEquals(1, closes.get(), "Closing an old generation must be idempotent");
        }
    }
}
