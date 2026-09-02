package org.kingdomfoxes.ralle.war.consumables;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Client context service owning gating, matching, and current highlight styles. */
public final class ConsumableHighlightService {
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final ConsumableHighlightStore store;
    private final ConsumableHighlightMatcher matcher;
    private final LongSupplier clock;

    public ConsumableHighlightService(
            Minecraft minecraft,
            BooleanSetting enabled,
            ConsumableHighlightStore store,
            LongSupplier clock
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.matcher = new ConsumableHighlightMatcher(store.snapshot());
        store.onChanged(matcher::update);
    }

    public ConsumableHighlightStore store() {
        return store;
    }

    public Optional<HighlightStyle> style(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !active()) return Optional.empty();
        return matcher.match(stack.getHoverName().getString());
    }

    public long timeMillis() {
        return clock.getAsLong();
    }

    public boolean active() {
        if (!enabled.value() || minecraft.getConnection() == null) return false;
        var server = minecraft.getCurrentServer();
        return server != null && WynncraftHost.matches(server.ip);
    }
}
