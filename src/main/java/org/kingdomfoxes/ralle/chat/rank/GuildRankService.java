package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns the opt-in, instance-local Fox-rank cache and its bounded refresh lifecycle. */
public final class GuildRankService {
    public static final Duration REFRESH_INTERVAL = Duration.ofMinutes(30);
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRankService.class);

    private final GuildRankGateway gateway;
    private final GuildRankCache cache;
    private final BooleanSetting internalRanksEnabled;
    private final ChoiceSetting rankStyle;
    private final Supplier<String> serverHost;
    private final LongSupplier clock;

    private volatile GuildRankSnapshot snapshot;
    private CompletableFuture<GuildRankSnapshot> refreshFuture;
    private long nextAutomaticRefresh;
    private boolean activeLastTick;
    private boolean refreshOnNextJoin = true;
    private volatile Throwable lastFailure;

    public GuildRankService(
            GuildRankGateway gateway,
            Path cachePath,
            BooleanSetting internalRanksEnabled,
            ChoiceSetting rankStyle,
            Supplier<String> serverHost,
            LongSupplier clock
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.cache = new GuildRankCache(Objects.requireNonNull(cachePath, "cachePath"));
        this.internalRanksEnabled = Objects.requireNonNull(internalRanksEnabled, "internalRanksEnabled");
        this.rankStyle = Objects.requireNonNull(rankStyle, "rankStyle");
        this.serverHost = Objects.requireNonNull(serverHost, "serverHost");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.snapshot = cache.load();
        this.nextAutomaticRefresh = snapshot.fetchedAtMillis() + REFRESH_INTERVAL.toMillis();
    }

    public void connectionChanged() {
        synchronized (this) {
            refreshOnNextJoin = true;
            activeLastTick = false;
        }
    }

    /** Called from the client tick; disabled and off-Wynncraft states are network-inert. */
    public void tick() {
        synchronized (this) {
            boolean active = internalRanksEnabled.value() && onWynncraft();
            if (!active) {
                activeLastTick = false;
                return;
            }
            long now = clock.getAsLong();
            boolean firstEnabledTick = !activeLastTick;
            activeLastTick = true;
            if (refreshOnNextJoin || firstEnabledTick || now >= nextAutomaticRefresh) {
                refreshOnNextJoin = false;
                beginRefresh(now);
            }
        }
    }

    public synchronized CompletableFuture<GuildRankSnapshot> requestRefresh() {
        if (!internalRanksEnabled.value() || !onWynncraft()) return CompletableFuture.completedFuture(snapshot);
        return beginRefresh(clock.getAsLong());
    }

    public Component apply(Component message) {
        if (!onWynncraft()) return message;
        GuildRankStyle style = GuildRankStyle.fromSetting(rankStyle.value());
        boolean useInternalRanks = internalRanksEnabled.value();
        if (!useInternalRanks && style == GuildRankStyle.TITLES) return message;
        return GuildRankTitleTransformer.apply(message, snapshot, style, useInternalRanks);
    }

    public synchronized boolean refreshing() {
        return refreshFuture != null && !refreshFuture.isDone();
    }

    public boolean canRefresh() {
        return internalRanksEnabled.value() && onWynncraft();
    }

    public Throwable lastFailure() {
        return lastFailure;
    }

    GuildRankSnapshot snapshot() {
        return snapshot;
    }

    private CompletableFuture<GuildRankSnapshot> beginRefresh(long now) {
        if (refreshFuture != null && !refreshFuture.isDone()) return refreshFuture;
        nextAutomaticRefresh = now + REFRESH_INTERVAL.toMillis();
        refreshFuture = gateway.fetchTitles().thenApply(titles -> {
            var refreshed = new GuildRankSnapshot(clock.getAsLong(), titles);
            if (refreshed.empty()) throw new IllegalArgumentException("Fox rank API returned no usable Fox titles");
            try {
                cache.save(refreshed);
            } catch (IOException exception) {
                LOGGER.warn("Could not save RALLE guild-rank cache", exception);
            }
            snapshot = refreshed;
            lastFailure = null;
            return refreshed;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                lastFailure = failure;
                LOGGER.warn("Could not refresh RALLE guild ranks; the previous local cache remains active", failure);
            }
        });
        return refreshFuture;
    }

    private boolean onWynncraft() {
        return WynncraftHost.matches(serverHost.get());
    }
}
