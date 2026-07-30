package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgLockDebouncerTest {
    @Test
    void togglingBackToTheAuthoritativeStateRefreshesDelayAndSendsNothing() {
        long[] now = {0};
        var debounce = new LfgLockDebouncer(() -> now[0]);
        var lobby = lobby(false);

        assertTrue(debounce.toggle(lobby, LfgLockDebouncer.Origin.KEYBIND).orElseThrow());
        now[0] = 500;
        assertFalse(debounce.toggle(lobby, LfgLockDebouncer.Origin.KEYBIND).orElseThrow());

        now[0] = 1_499;
        assertTrue(debounce.poll(lobby, true, false).isEmpty());
        assertEquals(false, debounce.desiredLocked(lobby.lobbyId()).orElseThrow());
        now[0] = 1_500;
        assertTrue(debounce.poll(lobby, true, false).isEmpty());
        assertTrue(debounce.desiredLocked(lobby.lobbyId()).isEmpty());
    }

    @Test
    void stableDesiredStateDispatchesOnceAfterOneSecond() {
        long[] now = {0};
        var debounce = new LfgLockDebouncer(() -> now[0]);
        var lobby = lobby(false);

        debounce.toggle(lobby, LfgLockDebouncer.Origin.SCREEN);
        now[0] = 999;
        assertTrue(debounce.poll(lobby, true, false).isEmpty());
        now[0] = 1_000;
        var command = debounce.poll(lobby, true, false).orElseThrow();

        assertEquals(lobby.lobbyId(), command.lobbyId());
        assertTrue(command.locked());
        assertEquals(LfgLockDebouncer.Origin.SCREEN, command.origin());
        assertTrue(debounce.dispatched(lobby.lobbyId()));
        assertTrue(debounce.toggle(lobby, LfgLockDebouncer.Origin.KEYBIND).isEmpty());
        assertTrue(debounce.poll(lobby, true, false).isEmpty());

        debounce.complete(lobby.lobbyId());
        assertFalse(debounce.dispatched(lobby.lobbyId()));
    }

    @Test
    void disconnectOrAuthorityLossCancelsPendingIntent() {
        var debounce = new LfgLockDebouncer(() -> 0);
        var lobby = lobby(false);
        debounce.toggle(lobby, LfgLockDebouncer.Origin.KEYBIND);

        assertTrue(debounce.poll(null, false, false).isEmpty());
        assertTrue(debounce.desiredLocked(lobby.lobbyId()).isEmpty());
    }

    private static LfgProtocol.Lobby lobby(boolean locked) {
        var guildId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        var hostId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var guild = new LfgProtocol.GuildIdentity(guildId, "Fox", "FOX", "#FF8200");
        var host = new LfgProtocol.Member(hostId, "Host", guild,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.RALLE,
                Instant.EPOCH, null);
        return new LfgProtocol.Lobby(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, locked,
                hostId, guildId, Instant.EPOCH, Instant.EPOCH, 1, 4, List.of(host),
                new LfgProtocol.LobbyCapabilities(false, false, Map.of()));
    }
}
