package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.nio.file.Path;
import java.util.UUID;

/** Temporary integration evidence: bounded local menu data, never credentials or ordinary inventory.
 * Does not infer identities, authorize a click, send a command or call a web API.
 * Remove this capture bridge after a verified standalone game adapter replaces it.
 */
public final class ClassMenuCapture implements AutoCloseable {
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final Path directory;
    private UUID account;
    private long stageUntil;
    private int captures;
    private final ClassMenuSnapshot contents = new ClassMenuSnapshot();
    private final ClassCaptureWriter writer = new ClassCaptureWriter();
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
        catch (RuntimeException failure) {
            stopped = true;
            org.slf4j.LoggerFactory.getLogger(ClassMenuCapture.class).warn("Could not capture class menu data", failure);
        }
    }

    private void captureMenu(UUID current) {
        // Read the live menu independently of its presentation: Wynntils may replace the screen.
        var menu = minecraft.player.containerMenu;
        if (menu == minecraft.player.inventoryMenu) return;
        if (!WynncraftClassMenu.matchesTitle(minecraft.screen.getTitle().getString())) return;
        if (menu.slots.size() < WynncraftClassMenu.MENU_SIZE) return;
        String title = minecraft.screen.getTitle().getString();
        if (contents.unchanged(menu, title, 0)) return;
        var decoded = WynncraftClassMenu.decode(title, contents.capture(menu, title, 0));
        // Preserve the four-snapshot budget for usable complete books, not packet fragments.
        if (decoded.isEmpty() || !decoded.get().complete()) return;
        var data = new JsonObject();
        data.addProperty("version", 1);
        data.addProperty("account", current.toString());
        data.addProperty("title", minecraft.screen.getTitle().getString());
        data.addProperty("containerId", menu.containerId);
        var slots = new JsonArray();
        var ops = minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var slot : menu.slots.subList(0, WynncraftClassMenu.MENU_SIZE)) {
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
        var destination = directory.resolve("class-menu-" + current + "-" + captures + ".json");
        if (!writer.submit(destination, data)) { stopped = true; return; }
        captures++;
    }

    private boolean available() {
        return enabled.value() && minecraft.getConnection() != null && minecraft.getCurrentServer() != null
                && WynncraftHost.matches(minecraft.getCurrentServer().ip);
    }
    public void reset() { account = null; stageUntil = 0; captures = 0; contents.clear(); stopped = false; }
    @Override public void close() { reset(); writer.close(); }
}
