package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Main-thread game adapter for discovery only. Opens no screens and issues no commands/clicks. */
public final class ClassMenuScanner {
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final ClassSelectorStore store;
    private final ClassDiscovery discovery;
    private final Path captures;
    private UUID account;
    private String title;
    private int openedId = -1;
    private AbstractContainerMenu lastMenu;
    private WynncraftClassMenu.Result lastResult;
    private boolean failed;
    private boolean autoOpenEnabled;

    public ClassMenuScanner(Minecraft minecraft, BooleanSetting enabled, ClassSelectorStore store, Path captures) {
        this.minecraft = minecraft;
        this.enabled = enabled;
        this.store = store;
        this.captures = captures;
        discovery = new ClassDiscovery(store);
    }
    public boolean requested() { return enabled.value(); }
    /** Current-session observation only; historical captures never satisfy this prerequisite. */
    public boolean autoOpenEnabled() {
        return available() && minecraft.getUser().getProfileId().equals(account) && autoOpenEnabled;
    }
    public record LiveMenu(int containerId, WynncraftClassMenu.Result result) { }

    /** Packet-proven book ownership, including a book whose contents never finished arriving. */
    public boolean ownsLiveBook(int containerId) {
        return available() && minecraft.player != null && containerId == openedId
                && WynncraftClassMenu.matchesTitle(title)
                && minecraft.player.containerMenu != minecraft.player.inventoryMenu
                && minecraft.player.containerMenu.containerId == containerId;
    }

    /** Read again immediately before an automated click; never use cached or diagnostic data. */
    public java.util.Optional<LiveMenu> liveMenu() {
        if (!available() || minecraft.player == null) return java.util.Optional.empty();
        var menu = minecraft.player.containerMenu;
        if (menu == minecraft.player.inventoryMenu || menu.containerId != openedId
                || menu.slots.size() < WynncraftClassMenu.MENU_SIZE) return java.util.Optional.empty();
        return WynncraftClassMenu.decode(title, menu.slots.stream().limit(WynncraftClassMenu.MENU_SIZE)
                .map(slot -> slot.getItem()).toList()).map(result -> {
                    if (minecraft.getUser().getProfileId().equals(account)) autoOpenEnabled = result.autoOpenEnabled();
                    return new LiveMenu(menu.containerId, result);
                });
    }
    public void opened(int containerId, String title) {
        if (!available()) return;
        openedId = containerId;
        this.title = title;
        lastMenu = null;
        lastResult = null;
    }

    public void tick() {
        if (!available()) { reset(); return; }
        if (minecraft.player == null || minecraft.level == null) return;
        UUID current = minecraft.getUser().getProfileId();
        if (!current.equals(account)) {
            account = current;
            autoOpenEnabled = false;
            discovery.session(current, true);
            try { recoverInitialCaptures(current); }
            catch (IOException failure) { failed(failure); }
        }
        if (failed) return;
        var menu = minecraft.player.containerMenu;
        if (menu == minecraft.player.inventoryMenu || menu.slots.size() < WynncraftClassMenu.MENU_SIZE) return;
        // Packet title remains valid when Wynntils replaces the vanilla container screen.
        String currentTitle = menu.containerId == openedId ? title
                : minecraft.screen == null ? "" : minecraft.screen.getTitle().getString();
        if (!WynncraftClassMenu.matchesTitle(currentTitle)) return;
        try {
            var items = menu.slots.stream().limit(WynncraftClassMenu.MENU_SIZE).map(slot -> slot.getItem()).toList();
            var decoded = WynncraftClassMenu.decode(currentTitle, items);
            if (decoded.isEmpty()) return;
            var result = decoded.get();
            autoOpenEnabled = result.autoOpenEnabled();
            if (menu == lastMenu && result.equals(lastResult)) return;
            store.migrateReferences(current, result.legacyReferences(), result.characters());
            discovery.observe(result.scan(current), discovery.generation());
            lastMenu = menu;
            lastResult = result;
        } catch (IOException | RuntimeException failure) { failed(failure); }
    }

    /** Upgrade the earlier capture-only build without requiring another round of manual attempts.
     * Existing saved accounts win. Captured evidence is historical and only ever merges.
     */
    private void recoverInitialCaptures(UUID current) throws IOException {
        if (!store.available() || minecraft.level == null) return;
        boolean initial = !store.hasAccount(current);
        var sprites = new java.util.HashMap<String, ClassSprite>();
        var aliases = new java.util.HashMap<String, String>();
        var proofs = new java.util.LinkedHashMap<String, ClassCharacter>();
        for (int index = 0; index < 4; index++) {
            var path = captures.resolve("class-menu-" + current + "-" + index + ".json");
            if (!Files.isRegularFile(path)) continue;
            byte[] bytes;
            try (var input = Files.newInputStream(path)) {
                bytes = input.readNBytes(ClassMenuEvidence.MAX_BYTES + 1);
            } catch (IOException inaccessible) {
                org.slf4j.LoggerFactory.getLogger(ClassMenuScanner.class).debug("Skipping unreadable class menu evidence {}", path);
                continue;
            }
            if (bytes.length > ClassMenuEvidence.MAX_BYTES) continue;
            try {
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) continue;
                var result = ClassMenuEvidence.decode(json.getAsJsonObject(), current,
                        minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE));
                if (result.isPresent()) {
                    aliases.putAll(result.get().legacyReferences());
                    result.get().characters().forEach(character -> proofs.put(character.id(), character));
                    if (initial) discovery.observe(new ClassScan(current, result.get().characters(), false), discovery.generation());
                    else result.get().characters().forEach(c -> { if (c.sprite() != null) sprites.put(c.id(), c.sprite()); });
                }
            } catch (com.google.gson.JsonParseException invalid) {
                // A diagnostic file cannot prevent live discovery or erase saved preferences.
            }
        }
        store.migrateReferences(current, aliases, java.util.List.copyOf(proofs.values()));
        if (!initial && !sprites.isEmpty()) store.updateSprites(current, sprites);
    }

    private void failed(Exception failure) {
        failed = true;
        org.slf4j.LoggerFactory.getLogger(ClassMenuScanner.class).warn("Could not update detected class entries", failure);
    }
    private boolean available() {
        return enabled.value() && minecraft.getConnection() != null && minecraft.getCurrentServer() != null
                && WynncraftHost.matches(minecraft.getCurrentServer().ip);
    }
    public void reset() {
        account = null;
        autoOpenEnabled = false;
        title = null;
        openedId = -1;
        lastMenu = null;
        lastResult = null;
        failed = false;
        discovery.disconnect();
    }
}
