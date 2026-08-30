package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleOnboardingNoticeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void newInstallPostsOnceAndPersistsNumericSentState() throws Exception {
        var statePath = temporaryDirectory.resolve("ralle-onboarding.properties");
        var delivered = new ArrayList<Component>();
        var notice = new RalleOnboardingNotice(statePath, false);

        assertEquals(0, notice.messageSent());
        assertTrue(notice.postIfNeeded(delivered::add));
        assertFalse(notice.postIfNeeded(delivered::add));
        assertEquals(1, delivered.size());
        assertEquals(1, notice.messageSent());
        assertTrue(Files.readString(statePath).contains("message-sent=1"));

        var restored = new RalleOnboardingNotice(statePath, false);
        assertFalse(restored.postIfNeeded(delivered::add));
        assertEquals(1, delivered.size());
    }

    @Test
    void existingInstallIsMigratedWithoutShowingTheNotice() throws Exception {
        Files.writeString(temporaryDirectory.resolve("ralle.properties"), "chat.compact-chat=true\n");
        assertTrue(RalleOnboardingNotice.hasExistingConfig(temporaryDirectory));

        var statePath = temporaryDirectory.resolve("ralle-onboarding.properties");
        var notice = new RalleOnboardingNotice(statePath,
                RalleOnboardingNotice.hasExistingConfig(temporaryDirectory));

        assertEquals(1, notice.messageSent());
        assertFalse(notice.postIfNeeded(ignored -> {}));
        assertTrue(Files.readString(statePath).contains("message-sent=1"));
    }

    @Test
    void earlierVanillaRalleKeybindAlsoIdentifiesAnExistingInstall() throws Exception {
        var configDirectory = temporaryDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Files.writeString(temporaryDirectory.resolve("options.txt"),
                "key_key.ralle.raid-lfg:key.keyboard.unknown\n");

        assertTrue(RalleOnboardingNotice.hasExistingConfig(configDirectory));
    }

    @Test
    void bodyUsesExactCopyAndClickableUnderlinedCommands() {
        var body = RalleOnboardingNotice.body();

        assertEquals("Thank you for installing RALLE! Certain features have been enabled by default but please explore the settings with "
                + "/ralle settings. Access the ally raid menu with /ralle lfg and edit hud elements with /ralle hud!\n\n"
                + "We hope to see you in the queues ;).", body.getString());

        for (var command : new String[]{"/ralle settings", "/ralle lfg", "/ralle hud"}) {
            var span = body.getSiblings().stream()
                    .filter(component -> component.getString().equals(command))
                    .findFirst()
                    .orElseThrow();
            assertTrue(span.getStyle().isUnderlined());
            assertEquals(command,
                    assertInstanceOf(ClickEvent.RunCommand.class, span.getStyle().getClickEvent()).command());
        }
    }

}
