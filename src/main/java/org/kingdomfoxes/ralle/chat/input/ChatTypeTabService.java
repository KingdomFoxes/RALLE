package org.kingdomfoxes.ralle.chat.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final String GUILD_PREFIX = "/g ";
    private static final String PARTY_PREFIX = "/p ";
    private static final String ALL_CHAT_PREFIX = "";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern DIRECT_MESSAGE = Pattern.compile(
            "^/?msg[\\t ]+([A-Za-z0-9_]{3,16})[\\t ]+(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private final Path storagePath;
    private String lastDirectMessageRecipient;

    public ChatTypeTabService() {
        this.storagePath = null;
    }

    public ChatTypeTabService(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        this.lastDirectMessageRecipient = loadRecipient();
    }

    public void observeSentCommand(String command) {
        Objects.requireNonNull(command, "command");
        var matcher = DIRECT_MESSAGE.matcher(command);
        if (matcher.matches() && !matcher.group(2).isBlank()) {
            var recipient = matcher.group(1);
            if (!recipient.equals(lastDirectMessageRecipient)) {
                lastDirectMessageRecipient = recipient;
                saveRecipient();
            }
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

    private String loadRecipient() {
        if (!Files.exists(storagePath)) return null;
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read chat type tabbing state from {}", storagePath, exception);
            return null;
        }

        var recipient = properties.getProperty(RECIPIENT_KEY);
        return recipient != null && USERNAME.matcher(recipient).matches() ? recipient : null;
    }

    private void saveRecipient() {
        if (storagePath == null) return;
        var properties = new Properties();
        properties.setProperty(RECIPIENT_KEY, lastDirectMessageRecipient);
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
}
