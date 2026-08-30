package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRankServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void remainsNetworkInertUntilEnabledOnWynncraftThenRefreshesEveryThirtyMinutes() throws Exception {
        var enabled = setting();
        var host = new String[]{"play.wynncraft.com:25565"};
        var now = new long[]{1_000L};
        var gateway = new TrackingGateway();
        var cachePath = temporaryDirectory.resolve("ralle-ranks.json");
        var service = new GuildRankService(gateway, cachePath, enabled, () -> host[0], () -> now[0]);

        service.tick();
        assertEquals(0, gateway.calls);

        enabled.set(true);
        service.tick();
        assertEquals(1, gateway.calls);
        assertEquals("KING CRICKET", service.snapshot().titleFor("maxkarson").orElseThrow());
        assertTrue(Files.exists(cachePath));

        now[0] += GuildRankService.REFRESH_INTERVAL.toMillis() - 1;
        service.tick();
        assertEquals(1, gateway.calls);
        now[0]++;
        service.tick();
        assertEquals(2, gateway.calls);

        host[0] = "example.org";
        now[0] += GuildRankService.REFRESH_INTERVAL.toMillis();
        service.tick();
        assertEquals(2, gateway.calls);
    }

    @Test
    void joinAndManualRefreshAreDeduplicatedAndKeepThePreviousCacheOnFailure() {
        var enabled = setting();
        enabled.set(true);
        var pending = new CompletableFuture<Map<String, String>>();
        var calls = new int[1];
        GuildRankGateway gateway = () -> {
            calls[0]++;
            return calls[0] == 1 ? pending : CompletableFuture.failedFuture(new IllegalStateException("offline"));
        };
        var service = new GuildRankService(gateway, temporaryDirectory.resolve("ranks.json"), enabled,
                () -> "wynncraft.com", () -> 10L);

        service.connectionChanged();
        service.tick();
        assertSame(service.requestRefresh(), service.requestRefresh());
        assertEquals(1, calls[0]);
        pending.complete(Map.of("maxkarson", "Knight"));
        assertEquals("KNIGHT", service.snapshot().titleFor("maxkarson").orElseThrow());

        service.requestRefresh().handle((ignored, failure) -> null).join();
        assertEquals(2, calls[0]);
        assertEquals("KNIGHT", service.snapshot().titleFor("maxkarson").orElseThrow());
        assertTrue(service.lastFailure() != null);
    }

    @Test
    void disabledFeatureDoesNotTransformChatEvenWhenACacheExists() throws Exception {
        var cache = new GuildRankCache(temporaryDirectory.resolve("ranks.json"));
        cache.save(new GuildRankSnapshot(1L, Map.of("maxkarson", "King Cricket")));
        var enabled = setting();
        var service = new GuildRankService(() -> CompletableFuture.completedFuture(Map.of()),
                temporaryDirectory.resolve("ranks.json"), enabled, () -> "wynncraft.com", () -> 2L);
        var message = Component.literal("ordinary chat");

        assertFalse(enabled.value());
        assertSame(message, service.apply(message));

        enabled.set(true);
        var offWynncraft = new GuildRankService(() -> CompletableFuture.completedFuture(Map.of()),
                temporaryDirectory.resolve("ranks.json"), enabled, () -> "example.org", () -> 2L);
        assertSame(message, offWynncraft.apply(message));
    }

    private static BooleanSetting setting() {
        return new BooleanSetting("internal-guild-ranks", Component.literal("Ranks"), Component.empty());
    }

    private static final class TrackingGateway implements GuildRankGateway {
        int calls;

        @Override
        public CompletableFuture<Map<String, String>> fetchTitles() {
            calls++;
            return CompletableFuture.completedFuture(Map.of("maxkarson", "King Cricket"));
        }
    }
}
