package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBehaviorServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void timestampsIndependentlyEnableTheProjectionPipeline() {
        var settings = new SettingsRegistry(temporaryDirectory.resolve("timestamps.properties"));
        RalleSettings.register(settings);
        var behavior = new ChatBehaviorService(null, settings);

        assertFalse(behavior.chatTimestampsEnabled());
        assertFalse(behavior.projectionEnabled());

        settings.setting("chat-timestamps", BooleanSetting.class).set(true);
        assertTrue(behavior.chatTimestampsEnabled());
        assertTrue(behavior.projectionEnabled());
    }

    @Test
    void legacyFullValueRemainsThePartialFullShadow() {
        assertEquals(
                ChatBehaviorService.TextShadow.PARTIAL_FULL,
                ChatBehaviorService.parseTextShadow("full")
        );
    }

    @Test
    void wrappedFullValueSelectsTheNewFullShadow() {
        assertEquals(
                ChatBehaviorService.TextShadow.FULL,
                ChatBehaviorService.parseTextShadow("wrapped-full")
        );
    }

    @Test
    void historyLimitIsVanillaUntilPersistentChatIsEnabled() {
        var settings = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(settings);
        var behavior = new ChatBehaviorService(null, settings);

        assertEquals(100, behavior.effectiveHistoryLimit());

        settings.setting("persistent-chat-enabled", BooleanSetting.class).set(true);
        assertEquals(500, behavior.effectiveHistoryLimit());

        for (var expected : new int[] {300, 500, 1000, 1500}) {
            settings.setting("persistent-chat-limit", ChoiceSetting.class).set(Integer.toString(expected));
            assertEquals(expected, behavior.effectiveHistoryLimit());
        }

        settings.setting("persistent-chat-enabled", BooleanSetting.class).set(false);
        assertEquals(100, behavior.effectiveHistoryLimit());
    }
}
