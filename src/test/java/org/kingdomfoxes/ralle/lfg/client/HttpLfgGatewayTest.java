package org.kingdomfoxes.ralle.lfg.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpLfgGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void packagedBuildTargetsInternalTestServer() {
        assertEquals("http://127.0.0.1:8001/api/ralle/v1", HttpLfgGateway.DEFAULT_BASE_URL);
    }

    @Test
    void mutationRetriesOneTransportFailureWithSameBearerAndIdempotencyKey() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        var keys = new ArrayList<String>();
        var authorizations = new ArrayList<String>();
        var requestIds = new ArrayList<String>();
        var operationIds = new ArrayList<String>();
        var attempts = new ArrayList<String>();
        server.createContext("/api/ralle/v1/lobbies/00000000-0000-0000-0000-000000000010/join", exchange -> {
            exchange.getRequestBody().readAllBytes();
            synchronized (keys) {
                keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                requestIds.add(exchange.getRequestHeaders().getFirst("X-Request-ID"));
                operationIds.add(exchange.getRequestHeaders().getFirst("X-Operation-ID"));
                attempts.add(exchange.getRequestHeaders().getFirst("X-Client-Attempt"));
            }
            if (calls.incrementAndGet() == 1) {
                exchange.close();
                return;
            }
            var response = mutationJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var gateway = new HttpLfgGateway(HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/ralle/v1");
        var key = UUID.fromString("00000000-0000-0000-0000-000000000099");
        var result = gateway.join("memory-only-token",
                UUID.fromString("00000000-0000-0000-0000-000000000010"), key)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, result.revision());
        assertEquals(2, calls.get());
        assertEquals(List.of(key.toString(), key.toString()), keys);
        assertEquals(List.of("Bearer memory-only-token", "Bearer memory-only-token"), authorizations);
        assertNotEquals(requestIds.get(0), requestIds.get(1));
        assertEquals(List.of(key.toString(), key.toString()), operationIds);
        assertEquals(List.of("1", "2"), attempts);
    }

    @Test
    void insecureNonLoopbackOverrideIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpLfgGateway(HttpClient.newHttpClient(), "http://example.org/api/ralle/v1"));
    }

    @Test
    void genericMissingEndpointBecomesVisibleHostActionError() {
        var error = HttpLfgGateway.httpError(404, "{\"detail\":\"Not Found\"}");

        assertEquals("HTTP_404", error.code());
        assertEquals("This action is unavailable on the connected Fox backend (HTTP 404).", error.message());
        assertFalse(error.retryable());
    }

    @Test
    void oversizedHttpResponseIsRejectedBeforeJsonParsing() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ralle/v1/status", exchange -> {
            var response = "x".repeat(HttpLfgGateway.MAX_HTTP_BODY_BYTES + 1)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var gateway = new HttpLfgGateway(HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/ralle/v1");

        var failure = assertThrows(ExecutionException.class,
                () -> gateway.status().get(5, TimeUnit.SECONDS));

        assertInstanceOf(org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException.class,
                failure.getCause());
    }

    private static String mutationJson() {
        return """
                {"protocol_version":1,"revision":1,"lobby":{
                  "lobby_id":"00000000-0000-0000-0000-000000000010","raid_type":"TNA","region":"EU","note":null,
                  "visibility":"PUBLIC","status":"OPEN","locked":false,
                  "host_minecraft_uuid":"00000000-0000-0000-0000-000000000001",
                  "host_guild_uuid":"00000000-0000-0000-0000-000000000100",
                  "created_at":"2026-07-19T20:00:00Z","last_activity_at":"2026-07-19T20:00:01Z",
                  "revision":1,"capacity":4,"members":[{
                    "minecraft_uuid":"00000000-0000-0000-0000-000000000001","ign":"Player01",
                    "guild":{"uuid":"00000000-0000-0000-0000-000000000100","name":"Kingdom of Foxes","tag":"FOX","color":"#FF8200"},
                    "role":"HOST","source":"RALLE","joined_at":"2026-07-19T20:00:00Z","discord_user_id":null
                  }],
                  "capabilities":{"join":true,"leave":false,"disabled_reasons":{"leave":"NOT_A_MEMBER"}}
                }}
                """;
    }
}
