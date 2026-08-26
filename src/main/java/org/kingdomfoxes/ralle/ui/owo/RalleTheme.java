package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.Color;
import net.minecraft.network.chat.Component;

final class RalleTheme {
    static final int BODY_LINE_HEIGHT = 12;
    static final int DARK_GOLD_ARGB = 0xFFB8832F;
    static final Color TEXT = Color.WHITE;
    static final Color MUTED = Color.ofRgb(0xA9B0BE);
    static final Color ACCENT = Color.ofRgb(0xF2B84B);
    static final Color POSITIVE = Color.ofRgb(0x67D391);
    static final Color DISABLED = Color.ofRgb(0x7E899B);

    private RalleTheme() {}

    static Component heading(Component component) {
        return RalleTypography.foxHeader(component);
    }

    static Component ui(Component component) {
        return RalleTypography.body(component);
    }

    /** Keeps the dropdown glyph in Minecraft's default font even when body copy uses Karla. */
    static Component dropdownLabel(Component component) {
        return Component.empty()
                .append(ui(component))
                .append(Component.literal(" ▾"));
    }
}
