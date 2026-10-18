package org.kingdomfoxes.ralle.classes;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Temporary integration evidence: bounded local menu data, never credentials or ordinary inventory.
 * Does not infer identities, authorize a click, send a command or call a web API.
 * Remove this capture bridge after a verified standalone game adapter replaces it.
 */
public final class ClassMenuCapture {
    private static final int MAX_BYTES = 256 * 1024;
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final Path directory;
    private UUID account;
    private long stageUntil;
    private int captures;
    private String previous;
    private boolean stopped;

    public ClassMenuCapture(Minecraft minecraft, BooleanSetting enabled, Path directory) {
        this.minecraft = minecraft;
        this.enabled = enabled;
        this.directory = directory;
    }

    public static boolean selectionPrompt(String value) {
        String plain = value.replaceAll("§.", "");
        return plain.contains("Left-Click to play") && plain.contains("Right-Click to switch");
    }

    public boolean requested() { return enabled.value(); }

    public void actionBar(String text) {
        if (available() && selectionPrompt(text)) {
            var current = minecraft.getUser().getProfileId();
            if (!current.equals(account)) { reset(); account = current; }
            stageUntil = System.nanoTime() / 1_000_000 + 15_000;
        }
    }

    public void tick() {
        if (!available()) { reset(); return; }
        UUID current = minecraft.getUser().getProfileId();
        if (!current.equals(account)) { reset(); account = current; }
        if (stopped || captures >= 4 || System.nanoTime() / 1_000_000 > stageUntil
                || minecraft.screen == null
                || minecraft.level == null || minecraft.player == null) return;
        try { captureMenu(current); }
        catch (RuntimeException | IOException failure) {
            stopped = true;
            org.slf4j.LoggerFactory.getLogger(ClassMenuCapture.class).warn("Could not capture class menu data", failure);
        }
    }

    private void captureMenu(UUID current) throws IOException {
        // Read the live menu independently of its presentation: Wynntils may replace the screen.
        var menu = minecraft.player.containerMenu;
        if (menu == minecraft.player.inventoryMenu) return;
        var data = new JsonObject();
        data.addProperty("version", 1);
        data.addProperty("account", current.toString());
        data.addProperty("title", minecraft.screen.getTitle().getString());
        data.addProperty("containerId", menu.containerId);
        var slots = new JsonArray();
        var ops = minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var slot : menu.slots) {
            if (slot.container == minecraft.player.getInventory() || slot.getItem().isEmpty()) continue;
            if (slots.size() >= 128) { stopped = true; return; }
            var item = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, slot.getItem()).result();
            if (item.isEmpty()) continue;
            var entry = new JsonObject();
            entry.addProperty("slot", slot.index);
            entry.add("item", item.get());
            slots.add(entry);
        }
        if (slots.isEmpty()) return;
        data.add("slots", slots);
        String signature = data.toString();
        if (signature.equals(previous)) return;
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) { stopped = true; return; }
        Files.createDirectories(directory);
        var destination = directory.resolve("class-menu-" + current + "-" + captures + ".json");
        var temporary = Files.createTempFile(directory, "class-menu-", ".tmp");
        try {
            Files.writeString(temporary, json);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        previous = signature;
        captures++;
    }

    private boolean available() {
        return enabled.value() && minecraft.getConnection() != null && minecraft.getCurrentServer() != null
                && WynncraftHost.matches(minecraft.getCurrentServer().ip);
    }
    public void reset() { account = null; stageUntil = 0; captures = 0; previous = null; stopped = false; }
}
