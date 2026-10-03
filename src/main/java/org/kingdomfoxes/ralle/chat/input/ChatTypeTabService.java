package org.kingdomfoxes.ralle.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Holds connection-local chat type and direct-message contact state.
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

    private final List<String> directMessageContacts = new ArrayList<>();
    private String selectedDirectMessageRecipient;
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
            selectedDirectMessageRecipient = addContact(matcher.group(1));
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

    /** Incoming DMs add a contact without changing the selected channel or draft destination. */
    public void observeIncomingSender(String sender) {
        if (sender != null && sender.matches("[A-Za-z0-9_]{3,16}")) {
            addContact(sender);
        }
    }

    /** Returns the last selected chat type for a newly opened empty chat. */
    public Optional<String> prefixForNewChat() {
        if (lastChatType == null) return Optional.empty();
        return switch (lastChatType) {
            case GUILD -> Optional.of(GUILD_PREFIX);
            case PARTY -> Optional.of(PARTY_PREFIX);
            case DIRECT_MESSAGE -> selectedDirectMessageRecipient == null
                    ? Optional.empty()
                    : Optional.of("/msg " + selectedDirectMessageRecipient + " ");
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
        } else if (DIRECT_MESSAGE_PREFIX.matcher(prefix).matches()
                && contact(prefix.substring(5).strip()) != null) {
            chatType = ChatType.DIRECT_MESSAGE;
            selectedDirectMessageRecipient = contact(prefix.substring(5).strip());
        } else {
            return;
        }

        lastChatType = chatType;
        inputSelectedAll = false;
    }

    /** Clears connection-local tab state after leaving a world or server. */
    public void resetSession() {
        directMessageContacts.clear();
        selectedDirectMessageRecipient = null;
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

        return Optional.of(nextChannel(lastInsertedPrefix));
    }

    /** Explicit label clicks cycle the destination even with a draft; the caller retains the body. */
    public String nextChannel(String currentPrefix) {
        if (currentPrefix == null) return GUILD_PREFIX;

        var prefixes = cyclePrefixes();
        int currentIndex = prefixes.indexOf(currentPrefix);
        if (currentIndex < 0 && DIRECT_MESSAGE_PREFIX.matcher(currentPrefix).matches()) {
            String known = contact(currentPrefix.substring(5).strip());
            if (known != null) currentIndex = prefixes.indexOf("/msg " + known + " ");
        }
        // An open screen may still hold a DM prefix that is no longer in this session.
        if (currentIndex < 0 && DIRECT_MESSAGE_PREFIX.matcher(currentPrefix).matches()) {
            return ALL_CHAT_PREFIX;
        }
        if (currentIndex < 0) return GUILD_PREFIX;
        return prefixes.get((currentIndex + 1) % prefixes.size());
    }

    private List<String> cyclePrefixes() {
        var prefixes = new ArrayList<String>();
        prefixes.add(GUILD_PREFIX);
        prefixes.add(PARTY_PREFIX);
        for (String contact : directMessageContacts) {
            prefixes.add("/msg " + contact + " ");
        }
        prefixes.add(ALL_CHAT_PREFIX);
        return List.copyOf(prefixes);
    }

    private static boolean matchesCompleteMessage(Pattern pattern, String command) {
        var matcher = pattern.matcher(command);
        return matcher.matches() && !matcher.group(1).isBlank();
    }

    private String addContact(String name) {
        String existing = contact(name);
        if (existing != null) return existing;
        directMessageContacts.add(name);
        return name;
    }

    private String contact(String name) {
        for (String existing : directMessageContacts) {
            if (existing.equalsIgnoreCase(name)) return existing;
        }
        return null;
    }

    private enum ChatType {
        GUILD,
        PARTY,
        DIRECT_MESSAGE,
        ALL
    }
}
