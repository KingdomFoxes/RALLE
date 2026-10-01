package org.kingdomfoxes.ralle.chat.rank;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpGuildRankGatewayTest {
    @Test void waitsForCredentialAndSendsBearerWithoutUpgrade() throws Exception {
        var calls = new AtomicInteger();
        var authorization = new AtomicReference<String>();
        var upgrade = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ranks", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            var body = "{\"members\":[{\"name\":\"Player\",\"fox_rank\":\"Sir\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders("Bearer rank-token".equals(authorization.get()) ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            var credentials = new CompletableFuture<String>();
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/ranks";
            var gateway = new HttpGuildRankGateway(() -> client, endpoint, () -> credentials);
            var result = gateway.fetchTitles();
            assertFalse(result.isDone());
            assertEquals(0, calls.get());
            credentials.complete("rank-token");
            assertEquals(Map.of("Player", "SIR"), result.get(5, TimeUnit.SECONDS));
            assertEquals("Bearer rank-token", authorization.get());
            assertNull(upgrade.get());
            var failed = new HttpGuildRankGateway(() -> client, endpoint,
                    () -> CompletableFuture.failedFuture(new IllegalStateException("auth failed")));
            assertThrows(Exception.class, () -> failed.fetchTitles().get(5, TimeUnit.SECONDS));
            var empty = new HttpGuildRankGateway(() -> client, endpoint,
                    () -> CompletableFuture.completedFuture(""));
            assertThrows(Exception.class, () -> empty.fetchTitles().get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
