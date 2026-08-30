package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

/** Vanilla-font recognition glyphs used exclusively by the keybind Action Bar. */
public enum LfgActionGlyph {
    CREATE("⛨"),
    KICK("☁"),
    LOCK("🔒"),
    UNLOCK("🔓"),
    PING("🔔"),
    REQUEUE("🔄"),
    LEAVE_DISBAND("❌");

    private final String symbol;

    LfgActionGlyph(String symbol) {
        this.symbol = symbol;
    }

    public Component symbol() {
        return Component.literal(symbol)
                .withStyle(style -> style.withFont(FontDescription.DEFAULT));
    }

    public Component label(String text) {
        return Component.empty()
                .append(symbol())
                .append(RalleTypography.body(Component.literal(" " + text)));
    }
}
