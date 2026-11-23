package org.kingdomfoxes.ralle.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.settings.LocalStateFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelsAbyssFlagTest {
    @TempDir Path directory;

    @Test
    void startsEnabledAtOneAndPersistsZeroOnceConsumed() throws Exception {
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        try (var state = new LocalStateFile(path)) {
            var flag = new WheelsAbyssFlag(state.section(WheelsAbyssFlag.SECTION_NAME));
            assertEquals(1, flag.value());
            assertTrue(flag.enabled());
            state.flush();

            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals(1, root.get("laterthatday").getAsInt());

            assertTrue(flag.consume());
            assertFalse(flag.consume());
            assertEquals(0, flag.value());
            assertFalse(flag.enabled());
            state.flush();

            root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals(0, root.get("laterthatday").getAsInt());
        }

        try (var reloaded = new LocalStateFile(path)) {
            var restored = new WheelsAbyssFlag(reloaded.section(WheelsAbyssFlag.SECTION_NAME));
            assertEquals(0, restored.value());
            assertFalse(restored.enabled());
            assertFalse(restored.consume());
        }
    }

    @Test
    void respectsManualEditsAndFailsSafeOnInvalidValues() throws Exception {
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        Files.writeString(path, "{\"schemaVersion\":1,\"laterthatday\":1}\n");
        try (var state = new LocalStateFile(path)) {
            assertTrue(new WheelsAbyssFlag(state.section(WheelsAbyssFlag.SECTION_NAME)).enabled());
        }

        Files.writeString(path, "{\"schemaVersion\":1,\"laterthatday\":\"0\"}\n");
        try (var state = new LocalStateFile(path)) {
            assertFalse(new WheelsAbyssFlag(state.section(WheelsAbyssFlag.SECTION_NAME)).enabled());
        }

        Files.writeString(path, "{\"schemaVersion\":1,\"laterthatday\":\"invalid\"}\n");
        try (var state = new LocalStateFile(path)) {
            assertFalse(new WheelsAbyssFlag(state.section(WheelsAbyssFlag.SECTION_NAME)).enabled());
        }
    }
}
