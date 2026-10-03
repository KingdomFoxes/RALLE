package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

/** Persists and presents the one-time local installation notice. */
public final class RalleOnboardingNotice {
    private static final Logger LOGGER = LoggerFactory.getLogger(RalleOnboardingNotice.class);
    private static final String MESSAGE_SENT_KEY = "message-sent";
    private static final List<String> LEGACY_CONFIG_FILES = List.of(
            "ralle.properties",
            "ralle-hud-layout.properties",
            "ralle-settings-ui.properties",
            "ralle-ranks.json"
    );

    private final Path storagePath;
    private int messageSent;

    public RalleOnboardingNotice(Path storagePath, boolean existingInstall) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        this.messageSent = load(existingInstall);
        if (!Files.exists(storagePath)) save();
    }

    public static boolean hasExistingConfig(Path configDirectory) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        if (LEGACY_CONFIG_FILES.stream().map(configDirectory::resolve).anyMatch(Files::exists)) return true;

        Path optionsPath = configDirectory.resolveSibling("options.txt");
        if (!Files.exists(optionsPath)) return false;
        try {
            return Files.readString(optionsPath).contains("key_key.ralle.");
        } catch (IOException exception) {
            LOGGER.warn("Could not inspect Minecraft options for an earlier RALLE installation", exception);
            return false;
        }
    }

    public synchronized boolean postIfNeeded(Consumer<Component> delivery) {
        Objects.requireNonNull(delivery, "delivery");
        if (messageSent == 1) return false;

        delivery.accept(body());
        messageSent = 1;
        save();
        return true;
    }

    public static Component body() {
        return Component.empty()
                .append("Thank you for installing RALLE! Certain features have been enabled by default but please explore the settings with ")
                .append(command("/ralle settings"))
                .append(". Access the ally raid menu with ")
                .append(command("/ralle lfg"))
                .append(" and edit hud elements with ")
                .append(command("/ralle hud"))
                .append("!\n\nWe hope to see you in the queues ;).");
    }

    private static Component command(String command) {
        return RalleChatMessages.clickable(command, new ClickEvent.RunCommand(command));
    }

    int messageSent() {
        return messageSent;
    }

    private int load(boolean existingInstall) {
        if (!Files.exists(storagePath)) return existingInstall ? 1 : 0;

        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
            String stored = properties.getProperty(MESSAGE_SENT_KEY);
            if ("0".equals(stored)) return 0;
            if ("1".equals(stored)) return 1;
            LOGGER.warn("Invalid RALLE onboarding state in {}; treating the notice as already sent", storagePath);
        } catch (IOException exception) {
            LOGGER.warn("Could not read RALLE onboarding state from {}; treating the notice as already sent",
                    storagePath, exception);
        }
        return 1;
    }

    private void save() {
        var properties = new Properties();
        properties.setProperty(MESSAGE_SENT_KEY, Integer.toString(messageSent));
        try {
            Files.createDirectories(storagePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                properties.store(writer, "RALLE local onboarding state");
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save RALLE onboarding state to {}", storagePath, exception);
        }
    }
}
