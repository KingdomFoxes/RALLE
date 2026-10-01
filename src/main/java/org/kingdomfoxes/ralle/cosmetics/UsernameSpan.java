package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/** Locates only the canonical IGN inside a server-composed label while retaining its font style. */
public record UsernameSpan(Component prefix, Component username) {
    public static Optional<UsernameSpan> find(Component fullLabel, String ign) {
        if (fullLabel == null || ign == null || !ign.matches("[A-Za-z0-9_]{1,16}")) return Optional.empty();
        String plain = fullLabel.getString();
        int start = plain.indexOf(ign);
        if (start < 0) return Optional.empty();
        int end = start + ign.length();
        MutableComponent prefix = Component.empty(), username = Component.empty();
        int[] cursor = {0};
        fullLabel.visit((style, text) -> {
            for (int offset = 0; offset < text.length();) {
                int codepoint = text.codePointAt(offset);
                String glyph = new String(Character.toChars(codepoint));
                if (cursor[0] < start) prefix.append(Component.literal(glyph).withStyle(style));
                else if (cursor[0] < end) username.append(Component.literal(glyph).withStyle(style));
                cursor[0] += Character.charCount(codepoint);
                offset += Character.charCount(codepoint);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return username.getString().equals(ign) ? Optional.of(new UsernameSpan(prefix, username)) : Optional.empty();
    }
}
