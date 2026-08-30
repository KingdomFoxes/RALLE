package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.Objects;

/** Temporary local-only guild rank joke; remove with the KING CRICKET experiment. */
public final class TemporaryGuildRankOverride {
    private static final int PILL_FOREGROUND_A = 0xE000;
    private static final int PILL_BACKGROUND_A = 0xE030;
    private static final int PILL_LEFT_CAP = 0xE060;
    private static final int PILL_SPACE_FILL = 0xE061;
    private static final int PILL_RIGHT_CAP = 0xE062;
    private static final int SPACING_ZERO = 0xD0000;
    private static final int NEGATIVE_ONE = SPACING_ZERO - 1;
    private static final int POSITIVE_TWO = SPACING_ZERO + 2;

    private static final String STRATEGIST_INDICATOR = strategistIndicator();
    private static final String STRATEGIST_BACKGROUND = background("STRATEGIST");
    private static final String STRATEGIST_FOREGROUND = foreground("STRATEGIST");
    private static final String KING_CRICKET_BACKGROUND = background("KING CRICKET");
    private static final String KING_CRICKET_FOREGROUND = foreground("KING CRICKET");

    private TemporaryGuildRankOverride() {}

    public static Component apply(Component message) {
        Objects.requireNonNull(message, "message");
        String renderedText = message.getString();
        if (!renderedText.contains(STRATEGIST_BACKGROUND)
                || !renderedText.contains(STRATEGIST_FOREGROUND)) {
            return message;
        }
        return transform(message);
    }

    private static Component transform(Component component) {
        MutableComponent replacement = null;
        if (component.getContents() instanceof PlainTextContents.LiteralContents literal) {
            replacement = replaceGlyphRuns(literal.text(), component.getStyle());
        }

        boolean ownTextChanged = replacement != null;
        boolean siblingChanged = false;
        var transformedSiblings = new Component[component.getSiblings().size()];
        for (int index = 0; index < component.getSiblings().size(); index++) {
            var sibling = component.getSiblings().get(index);
            transformedSiblings[index] = transform(sibling);
            siblingChanged |= transformedSiblings[index] != sibling;
        }

        if (!ownTextChanged && !siblingChanged) return component;

        MutableComponent transformed = ownTextChanged ? replacement : component.plainCopy();
        for (var sibling : transformedSiblings) transformed.append(sibling);
        return transformed;
    }

    private static MutableComponent replaceGlyphRuns(String text, Style originalStyle) {
        if (text.equals(STRATEGIST_INDICATOR)) {
            return Component.literal(STRATEGIST_INDICATOR)
                    .withStyle(originalStyle.withColor(ChatFormatting.AQUA));
        }
        if (text.equals(STRATEGIST_BACKGROUND)) {
            return Component.literal(KING_CRICKET_BACKGROUND)
                    .withStyle(originalStyle.withColor(ChatFormatting.AQUA));
        }
        if (text.equals(STRATEGIST_FOREGROUND)) {
            return Component.literal(KING_CRICKET_FOREGROUND).withStyle(originalStyle);
        }

        int cursor = 0;
        boolean changed = false;
        var result = Component.empty().withStyle(originalStyle);
        while (cursor < text.length()) {
            int indicatorIndex = text.indexOf(STRATEGIST_INDICATOR, cursor);
            int backgroundIndex = text.indexOf(STRATEGIST_BACKGROUND, cursor);
            int foregroundIndex = text.indexOf(STRATEGIST_FOREGROUND, cursor);
            int replacementIndex;
            int replacementKind;
            if (indicatorIndex >= 0
                    && (backgroundIndex < 0 || indicatorIndex < backgroundIndex)
                    && (foregroundIndex < 0 || indicatorIndex < foregroundIndex)) {
                replacementIndex = indicatorIndex;
                replacementKind = 0;
            } else if (backgroundIndex < 0) {
                replacementIndex = foregroundIndex;
                replacementKind = 2;
            } else if (foregroundIndex < 0 || backgroundIndex < foregroundIndex) {
                replacementIndex = backgroundIndex;
                replacementKind = 1;
            } else {
                replacementIndex = foregroundIndex;
                replacementKind = 2;
            }
            if (replacementIndex < 0) break;

            if (replacementIndex > cursor) {
                result.append(Component.literal(text.substring(cursor, replacementIndex)).withStyle(originalStyle));
            }
            if (replacementKind == 0) {
                result.append(Component.literal(STRATEGIST_INDICATOR)
                        .withStyle(originalStyle.withColor(ChatFormatting.AQUA)));
                cursor = replacementIndex + STRATEGIST_INDICATOR.length();
            } else if (replacementKind == 1) {
                result.append(Component.literal(KING_CRICKET_BACKGROUND)
                        .withStyle(originalStyle.withColor(ChatFormatting.AQUA)));
                cursor = replacementIndex + STRATEGIST_BACKGROUND.length();
            } else {
                result.append(Component.literal(KING_CRICKET_FOREGROUND).withStyle(originalStyle));
                cursor = replacementIndex + STRATEGIST_FOREGROUND.length();
            }
            changed = true;
        }
        if (!changed) return null;
        if (cursor < text.length()) {
            result.append(Component.literal(text.substring(cursor)).withStyle(originalStyle));
        }
        return result;
    }

    private static String strategistIndicator() {
        return new StringBuilder()
                .appendCodePoint(0xCFFFC)
                .appendCodePoint(0xE006)
                .appendCodePoint(0xCFFFF)
                .appendCodePoint(0xE002)
                .appendCodePoint(0xCFFFE)
                .toString();
    }

    private static String background(String title) {
        int foregroundWidth = 0;
        var result = new StringBuilder();
        result.appendCodePoint(PILL_LEFT_CAP).appendCodePoint(NEGATIVE_ONE);
        for (int index = 0; index < title.length(); index++) {
            char character = title.charAt(index);
            result.appendCodePoint(character == ' '
                    ? PILL_SPACE_FILL
                    : PILL_BACKGROUND_A + character - 'A');
            result.appendCodePoint(NEGATIVE_ONE);
            foregroundWidth += advance(character);
        }
        result.appendCodePoint(PILL_RIGHT_CAP);
        result.appendCodePoint(SPACING_ZERO - (foregroundWidth + 2));
        return result.toString();
    }

    private static String foreground(String title) {
        var result = new StringBuilder();
        for (int index = 0; index < title.length(); index++) {
            char character = title.charAt(index);
            if (character == ' ') result.append(' ');
            else result.appendCodePoint(PILL_FOREGROUND_A + character - 'A');
        }
        return result.appendCodePoint(POSITIVE_TWO).toString();
    }

    private static int advance(char character) {
        return character == 'I' || character == ' ' ? 4 : 6;
    }
}
