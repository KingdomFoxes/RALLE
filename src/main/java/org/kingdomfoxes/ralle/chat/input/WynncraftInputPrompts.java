package org.kingdomfoxes.ralle.chat.input;

import java.util.Locale;
import java.util.regex.Pattern;

/** Text-only recognition; callers must establish server and feature eligibility first. */
public final class WynncraftInputPrompts {
    private static final Pattern FORMATTING = Pattern.compile("§(?:#[0-9a-fA-F]{6,8}|[0-9a-fk-orA-FK-OR])");
    // Wynncraft's spacing glyphs occupy unassigned supplementary code points, not Unicode Co.
    private static final Pattern PRIVATE_GLYPHS = Pattern.compile("[\\p{Co}\\x{CFF00}-\\x{D00FF}]");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern MARKET = Pattern.compile(
            "type (?:the item name|the amount you wish to (?:buy|sell)|the price in emeralds"
                    + " or formatted \\(e\\.g '10eb', '10stx 5eb'\\)) or type 'cancel' to cancel:");
    private static final Pattern CHAT_INSTRUCTION = Pattern.compile(
            "(?:please )?(?:type|write|enter) (?:in chat .+|.+ in(?:to)? (?:the )?chat(?:[,.! :].*)?)");

    private WynncraftInputPrompts() {}

    public static boolean isPrompt(String raw) {
        String text = normalize(raw);
        if (MARKET.matcher(text).matches()) return true;
        // Anchored instructions exclude player names, quoted messages, and ordinary announcements.
        // Requiring an input subject excludes e.g. 'Type /help in chat'.
        return CHAT_INSTRUCTION.matcher(text).matches() && !text.contains("/")
                && (text.contains("name") || text.contains("nickname") || text.contains("price")
                || text.contains("amount") || text.contains("search") || text.contains("cancel"));
    }

    public static boolean isCancellation(String raw) {
        String text = normalize(raw);
        return text.equals("you moved and your chat input was canceled.")
                || text.equals("you moved and your chat input was cancelled.");
    }

    /** A click is only a candidate; a matching server close must follow. */
    public static boolean isInputAction(String title, String name, String lore) {
        String menu = normalize(title);
        String action = normalize(name);
        String help = normalize(lore);
        if (action.isEmpty() || help.contains("you cannot") || help.contains("you can't")
                || help.contains("no permission") || help.contains("requires a rank")) return false;
        if (menu.endsWith(": diplomacy") && action.equals("add ally")) {
            return true;
        }
        if ((menu.contains("pet") || action.contains("pet"))
                && action.matches("(?:rename(?: (?:your |this )?pet)?|(?:change|edit|set)(?: pet)? name|pet name)")) {
            return true;
        }
        if (menu.equals("recruit a friend") && action.equals("recruit a friend")) return true;
        // Some menus put the action on a right/left-click tooltip rather than a separate button.
        return help.contains("chat") && !help.contains("/")
                && (help.contains("type ") || help.contains("enter ") || help.contains("write "))
                && (help.contains("name") || help.contains("amount") || help.contains("price")
                || help.contains("search"));
    }

    static String normalize(String raw) {
        if (raw == null || raw.length() > 4096) return "";
        String text = FORMATTING.matcher(raw).replaceAll("");
        text = PRIVATE_GLYPHS.matcher(text).replaceAll("");
        return SPACE.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
