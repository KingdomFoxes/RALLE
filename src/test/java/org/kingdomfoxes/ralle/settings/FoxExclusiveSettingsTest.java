package org.kingdomfoxes.ralle.settings;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FoxExclusiveSettingsTest {
    @TempDir Path directory;

    @Test
    void savedEnabledValuesStayInactiveUntilFoxCheckAndSurviveUnrelatedSaves() throws Exception {
        var path = directory.resolve("ralle.properties");
        Files.writeString(path, "chat.internal-guild-ranks=true\nraid-lfg.raid-lfg-enabled=true\n"
                + "war.queue-kof-rank-colors=true\nwar.queue-attribution-enabled=true\n");
        var allowed = new AtomicBoolean();
        var settings = new SettingsRegistry(path);
        RalleSettings.register(settings);
        RalleSettings.requireFoxAccess(settings, allowed::get);
        settings.seal();
        for (var id : RalleSettings.FOX_EXCLUSIVE_IDS) {
            assertFalse(settings.setting(id, BooleanSetting.class).value());
            assertFalse(settings.available(id));
            assertTrue(settings.visible(id));
            assertTrue(settings.unavailableReason(id).isPresent());
        }
        assertFalse(settings.visible("new-party-notifications"));
        settings.setting("chat-timestamps", BooleanSetting.class).set(true);
        assertTrue(Files.readString(path).contains("raid-lfg.raid-lfg-enabled=true"));
        allowed.set(true);
        for (var id : RalleSettings.FOX_EXCLUSIVE_IDS) {
            assertTrue(settings.setting(id, BooleanSetting.class).value());
            assertTrue(settings.available(id));
        }
        assertTrue(settings.visible("new-party-notifications"));
        allowed.set(false);
        for (var id : RalleSettings.FOX_EXCLUSIVE_IDS)
            assertFalse(settings.setting(id, BooleanSetting.class).value());
        assertTrue(settings.setting("chat-timestamps", BooleanSetting.class).value());
        assertTrue(settings.setting(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID, BooleanSetting.class).value());
    }

    @Test
    void restoredLfgDefaultRemainsLockedUntilCurrentFoxMembershipIsConfirmed() throws Exception {
        var path = directory.resolve("fresh.properties");
        var allowed = new AtomicBoolean();
        var settings = new SettingsRegistry(path);
        RalleSettings.register(settings);
        RalleSettings.requireFoxAccess(settings, allowed::get);
        settings.seal();
        assertFalse(settings.setting(RalleSettings.RAID_LFG_ENABLED_ID, BooleanSetting.class).value());
        assertFalse(settings.visible("new-party-notifications"));
        assertTrue(Files.readString(path).contains("raid-lfg.raid-lfg-enabled=true"));
        allowed.set(true);
        assertTrue(settings.setting(RalleSettings.RAID_LFG_ENABLED_ID, BooleanSetting.class).value());
        assertTrue(settings.visible("new-party-notifications"));
        allowed.set(false);
        assertFalse(settings.setting(RalleSettings.RAID_LFG_ENABLED_ID, BooleanSetting.class).value());
    }

    @Test
    void runtimeAccessMasksEvenDefaultEnabledTogglesAndDirectMutations() {
        var settings = new SettingsRegistry(directory.resolve("defaults.properties"));
        var title = Component.literal("Example");
        var enabled = new BooleanSetting("enabled", title, title, true);
        settings.registerCategory(new SettingsCategory("example", title, title, List.of(enabled), List.of()));
        var allowed = new AtomicBoolean();
        settings.requireAccess("enabled", allowed::get, title);
        settings.seal();
        assertFalse(enabled.value());
        assertTrue(enabled.defaultValue());
        assertEquals("true", enabled.serialize());
        enabled.set(false);
        enabled.set(true);
        assertFalse(enabled.value());
        allowed.set(true);
        assertTrue(enabled.value());
        enabled.set(false);
        assertFalse(enabled.value());
    }
}
