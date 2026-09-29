package org.kingdomfoxes.ralle.platform;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.rank.HttpGuildRankGateway;
import org.kingdomfoxes.ralle.lfg.client.HttpLfgGateway;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SharedHttpTransportTest {
    @Test
    void unusedTransportNeverInitializesEvenWhenClosed() {
        var starts = new AtomicInteger();
        var transport = new SharedHttpTransport(() -> {
            starts.incrementAndGet();
            throw new AssertionError("Unused transport initialized");
        });
        new HttpLfgGateway(transport, HttpLfgGateway.PRODUCTION_BASE_URL);
        new HttpGuildRankGateway(transport, "https://kingdomfoxes.com/api/ranks",
                () -> { throw new AssertionError("Unused credential provider initialized"); });
        assertEquals(0, starts.get());
        transport.close();
        transport.close();
        assertEquals(0, starts.get());
        assertThrows(IllegalStateException.class, transport::get);
    }

    @Test
    void concurrentFirstUseCreatesOneClientAndShutdownStopsIt() throws Exception {
        var starts = new AtomicInteger();
        try (var transport = new SharedHttpTransport(() -> {
            starts.incrementAndGet();
            return HttpClient.newHttpClient();
        }); var workers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<HttpClient>>();
            for (int i = 0; i < 32; i++) jobs.add(workers.submit(transport::get));
            var client = jobs.getFirst().get(5, TimeUnit.SECONDS);
            for (var job : jobs) assertSame(client, job.get(5, TimeUnit.SECONDS));
            assertEquals(1, starts.get());
            transport.close();
            assertTrue(client.isTerminated() || client.awaitTermination(java.time.Duration.ofSeconds(5)));
        }
    }

    @Test
    void gatewaysShareFirstUseAndNeverShareBearerHeaders() throws Exception {
        var starts = new AtomicInteger();
        var requests = new AtomicInteger();
        var rankAuth = new AtomicReference<String>();
        var lfgAuth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            boolean ranks = exchange.getRequestURI().getPath().equals("/api/ranks");
            (ranks ? rankAuth : lfgAuth).set(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = (ranks ? "{\"members\":[]}"
                    : "{\"protocol_version\":1,\"revision\":0,\"viewer\":null,\"lobbies\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
            // The authenticated request only needs a rejection to exercise header isolation.
            exchange.sendResponseHeaders(ranks ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var transport = new SharedHttpTransport(() -> {
            starts.incrementAndGet();
            return HttpClient.newHttpClient();
        })) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var lfg = new HttpLfgGateway(transport, base + "/api/ralle/v1");
            var ranks = new HttpGuildRankGateway(transport, base + "/api/ranks",
                    () -> java.util.concurrent.CompletableFuture.completedFuture("rank-token"));
            assertEquals(0, starts.get());
            assertEquals(0, requests.get());
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> lfg.snapshot("test-token").get(5, TimeUnit.SECONDS));
            assertTrue(ranks.fetchTitles().get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(ranks.fetchTitles().get(5, TimeUnit.SECONDS).isEmpty());
            assertEquals(1, starts.get());
            assertEquals(3, requests.get());
            assertEquals("Bearer test-token", lfgAuth.get());
            assertEquals("Bearer rank-token", rankAuth.get());
        } finally { server.stop(0); }
    }
}
