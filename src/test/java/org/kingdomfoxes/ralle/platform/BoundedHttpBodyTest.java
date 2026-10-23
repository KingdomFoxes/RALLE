package org.kingdomfoxes.ralle.platform;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpBodyTest {
    @Test void collectsUtf8AcrossChunksAndRejectsOversizedBodiesWithoutBufferingThem() throws Exception {
        var subscriber = new BoundedHttpBody.Subscriber(4);
        var canceled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long count) {}
            @Override public void cancel() { canceled.set(true); }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {(byte)0xc3})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {(byte)0xa9})));
        subscriber.onComplete();
        assertEquals("é", subscriber.getBody().toCompletableFuture().get());
        assertFalse(canceled.get());

        var oversized = new BoundedHttpBody.Subscriber(4);
        oversized.onSubscribe(new Flow.Subscription() {
            @Override public void request(long count) {}
            @Override public void cancel() { canceled.set(true); }
        });
        oversized.onNext(List.of(ByteBuffer.wrap(new byte[3]), ByteBuffer.wrap(new byte[2])));
        oversized.onComplete();
        assertTrue(canceled.get());
        var failure = assertThrows(ExecutionException.class, () -> oversized.getBody().toCompletableFuture().get());
        assertInstanceOf(BoundedHttpBody.BodyLimitException.class, failure.getCause());
    }

    @Test void deadlineAbortsARealExchangeAfterHeadersArriveButTheBodyStalls() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var headersSent = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                headersSent.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            } finally { exchange.close(); }
        });
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                    .timeout(Duration.ofSeconds(1)).build();
            var response = BoundedHttpBody.send(client, request, 32);
            assertTrue(headersSent.await(3, TimeUnit.SECONDS));
            var failure = assertThrows(ExecutionException.class, () -> response.get(3, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());
            // Completion is insufficient: the transport must no longer retain its body.
            client.shutdown();
            assertTrue(client.awaitTermination(Duration.ofSeconds(3)));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
