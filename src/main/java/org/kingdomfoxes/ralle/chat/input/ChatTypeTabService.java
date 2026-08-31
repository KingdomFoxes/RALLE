package org.kingdomfoxes.ralle.chat.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Holds the last direct-message recipient used by chat type tabbing.
 * Command observation never changes, cancels, or resends the outgoing command.
 */
public final class ChatTypeTabService {
    public static final String SETTING_ID = "chat-type-tabbing";
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatTypeTabService.class);
    private static final String RECIPIENT_KEY = "last-direct-message-recipient";
    private static final String CHAT_TYPE_KEY = "last-chat-type";
    private static final String GUILD_PREFIX = "/g ";
    private static final String PARTY_PREFIX = "/p ";
    private static final String ALL_CHAT_PREFIX = "";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern DIRECT_MESSAGE = Pattern.compile(
            "^/?msg[\\t ]+([A-Za-z0-9_]{3,16})[\\t ]+(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GUILD_MESSAGE = Pattern.compile("^/?g[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_MESSAGE = Pattern.compile("^/?p[\\t ]+(.+)$", Pattern.CASE_INSENSITIVE);

    private final Path storagePath;
    private String lastDirectMessageRecipient;
    private ChatType lastChatType;

    public ChatTypeTabService() {
        this.storagePath = null;
    }

    public ChatTypeTabService(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        loadState();
    }

    public void observeSentCommand(String command) {
        Objects.requireNonNull(command, "command");
        var matcher = DIRECT_MESSAGE.matcher(command);
        if (matcher.matches() && !matcher.group(2).isBlank()) {
            var recipient = matcher.group(1);
            boolean changed = !recipient.equals(lastDirectMessageRecipient)
                    || lastChatType != ChatType.DIRECT_MESSAGE;
            lastDirectMessageRecipient = recipient;
            lastChatType = ChatType.DIRECT_MESSAGE;
            if (changed) saveState();
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

        if (chatType != lastChatType) {
            lastChatType = chatType;
            saveState();
        }
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

    private void loadState() {
        if (!Files.exists(storagePath)) return;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read chat type tabbing state from {}", storagePath, exception);
            return;
        }

        var recipient = properties.getProperty(RECIPIENT_KEY);
        if (recipient != null && USERNAME.matcher(recipient).matches()) {
            lastDirectMessageRecipient = recipient;
        }
        var chatType = properties.getProperty(CHAT_TYPE_KEY);
        if (chatType != null) {
            try {
                lastChatType = ChatType.valueOf(chatType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                lastChatType = null;
            }
        }
        if (lastChatType == ChatType.DIRECT_MESSAGE && lastDirectMessageRecipient == null) {
            lastChatType = null;
        }
    }

    private void saveState() {
        if (storagePath == null) return;
        var properties = new Properties();
        if (lastDirectMessageRecipient != null) {
            properties.setProperty(RECIPIENT_KEY, lastDirectMessageRecipient);
        }
        if (lastChatType != null) {
            properties.setProperty(CHAT_TYPE_KEY, lastChatType.name().toLowerCase(Locale.ROOT));
        }
        try {
            var parent = storagePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (var writer = Files.newBufferedWriter(storagePath)) {
                properties.store(writer, "RALLE chat input state");
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save chat type tabbing state to {}", storagePath, exception);
        }
    }

    private enum ChatType {
        GUILD,
        PARTY,
        DIRECT_MESSAGE,
        ALL
    }
}
