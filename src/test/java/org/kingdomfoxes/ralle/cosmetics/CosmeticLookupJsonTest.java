package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticLookupJsonTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    void acceptsCurrentGrantOnlyFoxResponseWithoutInventingSelection() {
        String body = """
                {"version":1,"players":[
                {"minecraft_uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                 "grants":["admin"],"revision":4}]}
                """;
        var result = CosmeticLookupJson.decode(body, List.of(PLAYER));
        assertEquals(java.util.Set.of("admin"), result.players().getFirst().grants());
        assertTrue(NameplateStyle.allowed("supporter-gold", result.players().getFirst().grants()));
        assertNull(result.players().getFirst().selectedStyleId());
        assertEquals(Duration.ofSeconds(30), result.ttl());
        var revoked = CosmeticLookupJson.decode(body.replace("[\"admin\"]", "[]"), List.of(PLAYER));
        assertTrue(revoked.players().getFirst().grants().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CosmeticLookupJson.decode(
                body.replace("\"version\":1", "\"version\":1,\"cache_ttl_seconds\":0"), List.of(PLAYER)));
        assertThrows(IllegalArgumentException.class, () -> CosmeticLookupJson.decode(
                body.replace("\"revision\":4", "\"revision\":4,\"selected_style_id\":42"), List.of(PLAYER)));
    }

    @Test
    void decodesPublishedFoxFixtureAndValidatesIdentity() {
        String body = """
                {"version":1,"cache_ttl_seconds":30,"players":[
                {"minecraft_uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","grants":["supporter"],
                 "selected_style_id":"supporter-gold","revision":1,"cache_ttl_seconds":30}]}
                """;
        assertEquals("{\"version\":1,\"uuids\":[\"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa\"]}",
                CosmeticLookupJson.request(List.of(PLAYER)));
        var result = CosmeticLookupJson.decode(body, List.of(PLAYER));
        assertEquals(Duration.ofSeconds(30), result.ttl());
        assertEquals("supporter-gold", result.players().getFirst().selectedStyle().id());
        assertThrows(IllegalArgumentException.class,
                () -> CosmeticLookupJson.decode(body.replace("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), List.of(PLAYER)));
        assertThrows(IllegalArgumentException.class,
                () -> CosmeticLookupJson.decode(body.replace("supporter-gold", "admin-red"), List.of(PLAYER)));
        assertThrows(IllegalArgumentException.class,
                () -> CosmeticLookupJson.decode(body.replace("\"revision\":1", "\"revision\":1.5"), List.of(PLAYER)));
    }

    @Test
    void neutralAndShorterTtlDoNotInventGrants() {
        String body = """
                {"version":1,"cache_ttl_seconds":30,"players":[
                {"minecraft_uuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","grants":[],
                 "selected_style_id":null,"revision":0,"cache_ttl_seconds":5}]}
                """;
        var result = CosmeticLookupJson.decode(body, List.of(PLAYER));
        assertNull(result.players().getFirst().selectedStyle());
        assertEquals(Duration.ofSeconds(5), result.ttl());
        assertThrows(IllegalArgumentException.class,
                () -> CosmeticLookupJson.request(List.of(PLAYER, PLAYER)));
    }
}
