package org.kingdomfoxes.ralle.lfg.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrictLfgJsonTest {
    @Test
    void statusAcceptsExactDocumentLimitAndRejectsOneExtraCharacter() {
        String status = "{\"enabled\":true,\"protocol_version\":1}";
        String bounded = status + " ".repeat(StrictLfgJson.MAX_DOCUMENT_CHARS - status.length());
        assertTrue(StrictLfgJson.decodeStatus(bounded).enabled());
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeStatus(bounded + " "));
    }

    @Test
    void liveFrameAcceptsExactDocumentLimitAndRejectsOneExtraCharacter() {
        String frame = "{\"type\":\"session.expiring\",\"protocol_version\":1,\"expires_at\":\"2026-07-19T20:15:00Z\"}";
        String bounded = frame + " ".repeat(StrictLfgJson.MAX_DOCUMENT_CHARS - frame.length());
        assertInstanceOf(LfgProtocol.SessionExpiringFrame.class, StrictLfgJson.decodeLiveFrame(bounded));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame(bounded + " "));
    }

    @Test
    void removalAcceptsOnlyBoundedStringReasonAndKeepsLegacyFramesCompatible() {
        var base = "{\"type\":\"lobby.remove\",\"protocol_version\":1,\"revision\":4,\"lobby_id\":\"00000000-0000-0000-0000-000000000010\"";
        assertNull(((LfgProtocol.RemoveFrame) StrictLfgJson.decodeLiveFrame(base + "}")).reason());
        assertEquals("lobby_expired", ((LfgProtocol.RemoveFrame) StrictLfgJson.decodeLiveFrame(
                base + ",\"reason\":\"lobby_expired\"}")).reason());
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame(base + ",\"reason\":5}"));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame(
                base + ",\"reason\":\"" + "x".repeat(65) + "\"}"));
    }

    private static final String LOBBY = """
            {
              "lobby_id":"00000000-0000-0000-0000-000000000010",
              "raid_type":"TNA","region":"EU","note":"chill run",
              "visibility":"PUBLIC","status":"OPEN","locked":false,
              "host_minecraft_uuid":"00000000-0000-0000-0000-000000000001",
              "host_guild_uuid":"00000000-0000-0000-0000-000000000100",
              "created_at":"2026-07-19T20:00:00Z","last_activity_at":"2026-07-19T20:00:01Z",
              "revision":2,"capacity":4,
              "members":[{
                "minecraft_uuid":"00000000-0000-0000-0000-000000000001","ign":"Player01",
                "guild":{"uuid":"00000000-0000-0000-0000-000000000100","name":"Kingdom of Foxes","tag":"FOX","color":"#FF8200"},
                "role":"HOST","source":"RALLE","joined_at":"2026-07-19T20:00:00Z","discord_user_id":null
              }],
              "capabilities":{"join":false,"leave":false,"disabled_reasons":{"join":"ALREADY_JOINED","leave":"HOST_MUST_DISBAND"}}
            }
            """;

    private static String snapshot() {
        return """
                {"protocol_version":1,"revision":2,
                 "viewer":{"minecraft_uuid":"00000000-0000-0000-0000-000000000001","ign":"Player01",
                   "guild":{"uuid":"00000000-0000-0000-0000-000000000100","name":"Kingdom of Foxes","tag":"FOX","color":"#FF8200"}},
                 "capabilities":{"create":false,"browse":true,"disabled_reasons":{"create":"PLAYER_ALREADY_ACTIVE"}},
                 "lobbies":[%s]}
                """.formatted(LOBBY);
    }

    @Test
    void guildlessViewerCanAuthenticateAndSyncWithoutAllowingGuildlessLobbyMembers() {
        String guild = "{\"uuid\":\"00000000-0000-0000-0000-000000000100\",\"name\":\"Kingdom of Foxes\",\"tag\":\"FOX\",\"color\":\"#FF8200\"}";
        String guildless = snapshot().replaceFirst(java.util.regex.Pattern.quote(guild), "null");
        assertNull(StrictLfgJson.decodeSnapshot(guildless).viewer().guild());
        assertNotNull(StrictLfgJson.decodeSnapshot(guildless).lobbies().getFirst().members().getFirst().guild());
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replace(guild, "null")));
        String session = """
                {"access_token":"token","token_type":"Bearer","expires_in":900,
                 "expires_at":"2026-07-19T20:15:00Z","protocol_version":1,
                 "player":{"minecraft_uuid":"00000000-0000-0000-0000-000000000001","ign":"Player01","guild":null}}
                """;
        assertNull(StrictLfgJson.decodeSession(session).player().guild());
    }

    @Test
    void decodesMinimalStatusAndRejectsLegacyOrUnknownFields() {
        var status = StrictLfgJson.decodeStatus("{\"enabled\":true,\"protocol_version\":1}");
        assertTrue(status.enabled());
        assertEquals(1, status.protocolVersion());
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeStatus(
                "{\"enabled\":true,\"protocol_version\":1,\"legacy_requirement\":\"old\"}"));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeStatus(
                "{\"enabled\":true,\"protocol_version\":1,\"future\":true}"));
    }

    @Test
    void encodesChallengeWithoutModBuildVersion() {
        assertEquals(
                "{\"minecraft_uuid\":\"00000000-0000-0000-0000-000000000001\",\"ign\":\"Player01\",\"protocol_version\":1}",
                StrictLfgJson.challengeRequest(
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), "Player01"));
    }

    @Test
    void decodesCompleteSnapshotAndLiveFrames() {
        var snapshot = StrictLfgJson.decodeSnapshot(snapshot());
        assertEquals(2, snapshot.revision());
        assertEquals(LfgProtocol.RaidType.TNA, snapshot.lobbies().getFirst().raidType());
        assertEquals("FOX", snapshot.lobbies().getFirst().members().getFirst().guild().tag());

        var live = StrictLfgJson.decodeLiveFrame(snapshot().replaceFirst("\\{", "{\"type\":\"snapshot\","));
        assertInstanceOf(LfgProtocol.SnapshotFrame.class, live);
        var upsert = StrictLfgJson.decodeLiveFrame("{\"type\":\"lobby.upsert\",\"protocol_version\":1,\"revision\":3,\"lobby\":" + LOBBY + "}");
        assertInstanceOf(LfgProtocol.UpsertFrame.class, upsert);
        var remove = StrictLfgJson.decodeLiveFrame("{\"type\":\"lobby.remove\",\"protocol_version\":1,\"revision\":4,\"lobby_id\":\"00000000-0000-0000-0000-000000000010\"}");
        assertInstanceOf(LfgProtocol.RemoveFrame.class, remove);
        var expiring = StrictLfgJson.decodeLiveFrame("{\"type\":\"session.expiring\",\"protocol_version\":1,\"expires_at\":\"2026-07-19T20:15:00Z\"}");
        assertInstanceOf(LfgProtocol.SessionExpiringFrame.class, expiring);
        var ping = StrictLfgJson.decodeLiveFrame("""
                {"type":"party.ping","protocol_version":1,
                 "event_id":"00000000-0000-0000-0000-000000000020",
                 "lobby_id":"00000000-0000-0000-0000-000000000010",
                 "host_minecraft_uuid":"00000000-0000-0000-0000-000000000001",
                 "host_ign":"Player01","occurred_at":"2026-07-19T20:02:00Z"}
                """);
        assertInstanceOf(LfgProtocol.PartyPingFrame.class, ping);
        var command = StrictLfgJson.decodeLiveFrame("""
                {"type":"party.kick-command","protocol_version":1,
                 "event_id":"00000000-0000-0000-0000-000000000021",
                 "lobby_id":"00000000-0000-0000-0000-000000000010",
                 "target_minecraft_uuid":"00000000-0000-0000-0000-000000000002",
                 "target_ign":"Player02","occurred_at":"2026-07-19T20:02:00Z"}
                """);
        assertInstanceOf(LfgProtocol.PartyKickCommandFrame.class, command);
    }

    @Test
    void protocolV1RejectsInternalCauseOnLobbyFrames() {
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame(
                "{\"type\":\"lobby.upsert\",\"protocol_version\":1,\"revision\":3,\"cause\":\"CREATE\",\"lobby\":"
                        + LOBBY + "}"));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame(
                "{\"type\":\"lobby.remove\",\"protocol_version\":1,\"revision\":4,\"cause\":\"DISBAND\","
                        + "\"lobby_id\":\"00000000-0000-0000-0000-000000000010\"}"));
    }

    @Test
    void rejectsMissingUnknownInvalidAndMalformedFields() {
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replace("\"revision\":2,", "")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replaceFirst("\\{", "{\"surprise\":true,")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replace("\"raid_type\":\"TNA\"", "\"raid_type\":\"UNKNOWN\"")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replace("00000000-0000-0000-0000-000000000010", "not-a-uuid")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(snapshot().replace("\"capacity\":4", "\"capacity\":5")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame("{\"type\":\"future.frame\"}"));
    }

    @Test
    void rejectsHostileNotesAndOversizedDocuments() {
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(
                snapshot().replace("\"note\":\"chill run\"", "\"note\":\"bad\\u202etxt\"")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(
                snapshot().replace("\"note\":\"chill run\"", "\"note\":\"\\u00a7cpretend\"")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeSnapshot(
                snapshot().replace("\"note\":\"chill run\"", "\"note\":\"" + "x".repeat(81) + "\"")));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeStatus(
                "{\"enabled\":true,\"protocol_version\":1}" + " ".repeat(StrictLfgJson.MAX_DOCUMENT_CHARS)));
    }

    @Test
    void createRequestAppliesTheSamePlainTextPolicy() {
        assertEquals("{\"raid_type\":\"TNA\",\"region\":\"EU\",\"note\":\"Hello world\"}",
                StrictLfgJson.createRequest(LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU,
                        " \u00a7cHello\n\u202eworld "));
    }

    @Test
    void encodesStrictHostActionBodies() {
        assertEquals(
                "{\"target_minecraft_uuid\":\"00000000-0000-0000-0000-000000000002\"}",
                StrictLfgJson.kickRequest(
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertEquals("{\"locked\":true}", StrictLfgJson.lockRequest(true));
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeLiveFrame("""
                {"type":"party.kick-command","protocol_version":1,
                 "event_id":"00000000-0000-0000-0000-000000000021",
                 "lobby_id":"00000000-0000-0000-0000-000000000010",
                 "target_minecraft_uuid":"00000000-0000-0000-0000-000000000002",
                 "target_ign":"bad command","occurred_at":"2026-07-19T20:02:00Z"}
                """));
    }

    @Test
    void decodesStructuredRaidAlreadyListedLobby() {
        var error = StrictLfgJson.decodeError("""
                {"error":{"code":"RAID_ALREADY_LISTED","message":"Already listed","retryable":false,
                 "lobby_id":"00000000-0000-0000-0000-000000000010","details":{"lobby":%s}}}
                """.formatted(LOBBY));
        assertEquals("RAID_ALREADY_LISTED", error.code());
        assertNotNull(error.returnedLobby());
    }

    @Test
    void decodesRateLimitRetryAndRejectsUnknownErrorDetails() {
        var error = StrictLfgJson.decodeError("""
                {"error":{"code":"RATE_LIMITED","message":"Slow down","retryable":true,
                 "details":{"retry_after":17}}}
                """);
        assertEquals(17, error.retryAfterSeconds());
        assertThrows(LfgProtocolException.class, () -> StrictLfgJson.decodeError("""
                {"error":{"code":"RATE_LIMITED","message":"Slow down","retryable":true,
                 "details":{"future":17}}}
                """));
    }
}
