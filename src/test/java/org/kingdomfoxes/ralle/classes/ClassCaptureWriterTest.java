package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClassCaptureWriterTest {
    @TempDir Path directory;

    @Test void shutdownDrainsDetachedCapturesAndPreservesTheDiagnosticFormat() throws Exception {
        var writer = new ClassCaptureWriter();
        for (int i = 0; i < 4; i++) {
            var snapshot = new JsonObject();
            snapshot.addProperty("version", 1);
            snapshot.addProperty("containerId", i);
            assertTrue(writer.submit(directory.resolve("class-" + i + ".json"), snapshot));
        }
        writer.close();
        for (int i = 0; i < 4; i++) {
            var data = JsonParser.parseString(Files.readString(directory.resolve("class-" + i + ".json"))).getAsJsonObject();
            assertEquals(1, data.get("version").getAsInt());
            assertEquals(i, data.get("containerId").getAsInt());
        }
        assertFalse(writer.submit(directory.resolve("late.json"), new JsonObject()));
    }
}
