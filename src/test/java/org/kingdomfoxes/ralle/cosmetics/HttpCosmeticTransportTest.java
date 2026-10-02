package org.kingdomfoxes.ralle.cosmetics;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpCosmeticTransportTest {
    @Test
    void lookupAndSelfRequestsSendJsonWithoutHttp2Upgrade() throws Exception {
        var player = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var lookupBody = new AtomicReference<String>();
        var selectionBody = new AtomicReference<String>();
        var identity = """
                {"version":1,"minecraft_uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                 "grants":["supporter"],"selected_style_id":"supporter-gold",
                 "revision":1,"cache_ttl_seconds":30}
                """;
        server.createContext("/", exchange -> {
            boolean lookup = exchange.getRequestURI().getPath().endsWith("/lookup");
            (lookup ? lookupBody : selectionBody).set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            // Uvicorn's HTTP/1.1 endpoint cannot accept Java's cleartext h2c upgrade.
            boolean upgrade = exchange.getRequestHeaders().containsKey("Upgrade");
            String responseIdentity = !lookup && JsonParser.parseString(selectionBody.get()).getAsJsonObject()
                    .get("style_id").isJsonNull()
                    ? identity.replace("\"supporter-gold\"", "null").replace("\"revision\":1", "\"revision\":2")
                    : identity;
            var body = (lookup ? "{\"version\":1,\"cache_ttl_seconds\":30,\"players\":[" + identity + "]}" : responseIdentity)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(upgrade ? 422 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var result = new HttpNameplateDirectory(() -> client, base + "/lookup")
                    .lookup(List.of(player)).get(5, TimeUnit.SECONDS);
            assertEquals("supporter-gold", result.players().getFirst().selectedStyleId());
            assertEquals(CosmeticLookupJson.request(List.of(player)), lookupBody.get());
            var selected = new HttpCosmeticSelfGateway(() -> client, base)
                    .select("test-token", "supporter-gold").get(5, TimeUnit.SECONDS);
            assertEquals("supporter-gold", selected.selectedStyleId());
            var sent = JsonParser.parseString(selectionBody.get()).getAsJsonObject();
            assertEquals(1, sent.get("version").getAsInt());
            assertEquals("supporter-gold", sent.get("style_id").getAsString());
            // None sends an explicit JSON null and accepts Fox's cleared selection.
            var cleared = new HttpCosmeticSelfGateway(() -> client, base)
                    .select("test-token", null).get(5, TimeUnit.SECONDS);
            assertNull(cleared.selectedStyle());
            var clearBody = JsonParser.parseString(selectionBody.get()).getAsJsonObject();
            assertEquals(1, clearBody.get("version").getAsInt());
            assertTrue(clearBody.has("style_id"));
            assertTrue(clearBody.get("style_id").isJsonNull());
        } finally {
            server.stop(0);
        }
    }
}
