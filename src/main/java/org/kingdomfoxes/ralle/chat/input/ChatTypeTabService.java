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
    private static final Pattern DIRECT_MESSAGE = Pattern.compile(
            "^/?msg[\\t ]+([A-Za-z0-9_]{3,16})[\\t ]+(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GUILD_MESSAGE = Pattern.compile("^/?g[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_MESSAGE = Pattern.compile("^/?p[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);

    private String lastDirectMessageRecipient;
    private ChatType lastChatType;

    public void observeSentCommand(String command) {
        Objects.requireNonNull(command, "command");
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
    }

    /** Clears connection-local tab state after leaving a world or server. */
    public void resetSession() {
        lastDirectMessageRecipient = null;
        lastChatType = null;
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
