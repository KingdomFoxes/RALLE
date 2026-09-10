package org.kingdomfoxes.ralle.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Holds connection-local chat type and direct-message recipient state.
 * Command observation never changes, cancels, or resends the outgoing command.
 */
public final class ChatTypeTabService {
    public static final String SETTING_ID = "chat-type-tabbing";
    private static final String GUILD_PREFIX = "/g ";
    private static final String PARTY_PREFIX = "/p ";
    private static final String ALL_CHAT_PREFIX = "";
    private static final Pattern DIRECT_MESSAGE_PREFIX = Pattern.compile("/msg [A-Za-z0-9_]{3,16} ");
    private static final Pattern DIRECT_MESSAGE = Pattern.compile(
            "^/?msg[\\t ]+([A-Za-z0-9_]{3,16})[\\t ]+(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GUILD_MESSAGE = Pattern.compile("^/?g[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_MESSAGE = Pattern.compile("^/?p[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_PREFIX = Pattern.compile(
            "^/(?:g |p |msg [A-Za-z0-9_]{3,16} )", Pattern.CASE_INSENSITIVE);

    private String lastDirectMessageRecipient;
    private ChatType lastChatType;
    private long inputRequestRevision;
    private boolean inputSelectedAll;

    /** One visible switch, not a lock: subsequent explicit channel selections are respected. */
    public void selectAllForInput() {
        lastChatType = ChatType.ALL;
        inputSelectedAll = true;
        inputRequestRevision++;
    }

    public long inputRequestRevision() { return inputRequestRevision; }
    public boolean inputSelectedAll() { return inputSelectedAll; }

    /** Native chat colors: guild aqua, party yellow, direct messages pink, all white. */
    public static int channelColor(String prefix) {
        if (GUILD_PREFIX.equals(prefix)) return 0xFF55FFFF;
        if (PARTY_PREFIX.equals(prefix)) return 0xFFFFFF55;
        if (prefix != null && DIRECT_MESSAGE_PREFIX.matcher(prefix).matches()) return 0xFFFF55FF;
        return 0xFFFFFFFF;
    }

    /** Presentation is separate from the editable body and never goes on the wire. */
    public static String channelLabel(String prefix) {
        if (GUILD_PREFIX.equals(prefix)) return "[Guild]";
        if (PARTY_PREFIX.equals(prefix)) return "[Party]";
        if (prefix != null && DIRECT_MESSAGE_PREFIX.matcher(prefix).matches()) {
            return "[" + prefix.substring(5).strip() + "]";
        }
        return "[All]";
    }

    /** Recognizes explicit prefixes from typing, paste, history, or saved drafts. */
    public static Optional<String> explicitPrefix(String input) {
        var matcher = EXPLICIT_PREFIX.matcher(input);
        if (!matcher.find()) return Optional.empty();
        String prefix = matcher.group();
        return Optional.of(prefix.regionMatches(true, 0, "/msg ", 0, 5)
                ? "/msg " + prefix.substring(5) : prefix.toLowerCase(java.util.Locale.ROOT));
    }

    public static String outgoingMessage(String body, String prefix) {
        if (body.isBlank() || body.stripLeading().startsWith("/") || prefix == null) return body;
        return prefix + body;
    }

    public void observeSentCommand(String command) {
        Objects.requireNonNull(command, "command");
        inputSelectedAll = false;
        var matcher = DIRECT_MESSAGE.matcher(command);
        if (matcher.matches() && !matcher.group(2).isBlank()) {
            var recipient = matcher.group(1);
            lastDirectMessageRecipient = recipient;
            lastChatType = ChatType.DIRECT_MESSAGE;
        } else if (matchesCompleteMessage(GUILD_MESSAGE, command)) {
            rememberPrefix(GUILD_PREFIX);
        } else if (matchesCompleteMessage(PARTY_MESSAGE, command)) {
            rememberPrefix(PARTY_PREFIX);
        }
    }

    public void observeSentChat(String message) {
        Objects.requireNonNull(message, "message");
        if (!message.isBlank()) rememberPrefix(ALL_CHAT_PREFIX);
    }

    /** Incoming DMs update the cycle, without selecting a different chat type. */
    public void observeIncomingSender(String sender) {
        if (sender != null && sender.matches("[A-Za-z0-9_]{3,16}")) {
            lastDirectMessageRecipient = sender;
        }
    }

    public String refreshEmptyChannel(String prefix, String body) {
        return body.isEmpty() && prefix != null && DIRECT_MESSAGE_PREFIX.matcher(prefix).matches()
                && lastDirectMessageRecipient != null ? "/msg " + lastDirectMessageRecipient + " " : prefix;
    }

    /** Returns the last selected chat type for a newly opened empty chat. */
    public Optional<String> prefixForNewChat() {
        if (lastChatType == null) return Optional.empty();
        return switch (lastChatType) {
            case GUILD -> Optional.of(GUILD_PREFIX);
            case PARTY -> Optional.of(PARTY_PREFIX);
            case DIRECT_MESSAGE -> lastDirectMessageRecipient == null
                    ? Optional.empty()
                    : Optional.of("/msg " + lastDirectMessageRecipient + " ");
            case ALL -> Optional.of(ALL_CHAT_PREFIX);
        };
    }

    /** Remembers a prefix produced by this service as the active chat type. */
    public void rememberPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        ChatType chatType;
        if (GUILD_PREFIX.equals(prefix)) {
            chatType = ChatType.GUILD;
        } else if (PARTY_PREFIX.equals(prefix)) {
            chatType = ChatType.PARTY;
        } else if (ALL_CHAT_PREFIX.equals(prefix)) {
            chatType = ChatType.ALL;
        } else if (lastDirectMessageRecipient != null
                && prefix.equals("/msg " + lastDirectMessageRecipient + " ")) {
            chatType = ChatType.DIRECT_MESSAGE;
        } else {
            return;
        }

        lastChatType = chatType;
        inputSelectedAll = false;
    }

    /** Clears connection-local tab state after leaving a world or server. */
    public void resetSession() {
        lastDirectMessageRecipient = null;
        lastChatType = null;
        inputSelectedAll = false;
        inputRequestRevision++;
    }

    /**
     * Returns the next prefix only for an empty input or the exact prefix most
     * recently inserted by RALLE. Any edit or message draft is left to vanilla.
     */
    public Optional<String> nextPrefix(String currentInput, String lastInsertedPrefix) {
        Objects.requireNonNull(currentInput, "currentInput");
        if (currentInput.isEmpty() && lastInsertedPrefix == null) return Optional.of(GUILD_PREFIX);
        if (lastInsertedPrefix == null || !currentInput.equals(lastInsertedPrefix)) return Optional.empty();

        var prefixes = cyclePrefixes();
        int currentIndex = prefixes.indexOf(lastInsertedPrefix);
        // An open screen may still hold the previous DM target (especially a draft).
        if (currentIndex < 0 && DIRECT_MESSAGE_PREFIX.matcher(lastInsertedPrefix).matches()) {
            return Optional.of(ALL_CHAT_PREFIX);
        }
        if (currentIndex < 0) return Optional.of(GUILD_PREFIX);
        return Optional.of(prefixes.get((currentIndex + 1) % prefixes.size()));
    }

    private List<String> cyclePrefixes() {
        var prefixes = new ArrayList<String>();
        prefixes.add(GUILD_PREFIX);
        prefixes.add(PARTY_PREFIX);
        if (lastDirectMessageRecipient != null) {
            prefixes.add("/msg " + lastDirectMessageRecipient + " ");
        }
        prefixes.add(ALL_CHAT_PREFIX);
        return List.copyOf(prefixes);
    }

    private static boolean matchesCompleteMessage(Pattern pattern, String command) {
        var matcher = pattern.matcher(command);
        return matcher.matches() && !matcher.group(1).isBlank();
    }

    private enum ChatType {
        GUILD,
        PARTY,
        DIRECT_MESSAGE,
        ALL
    }
}
