package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Reads only bounded, account-matching local captures through the same decoder as live data. */
public final class ClassMenuEvidence {
    public static final int MAX_BYTES = 256 * 1024;
    private ClassMenuEvidence() {}

    public static Optional<WynncraftClassMenu.Result> decode(JsonObject evidence, UUID account, DynamicOps<JsonElement> ops) {
        try {
            if (evidence.get("version").getAsInt() != 1
                    || !account.toString().equals(evidence.get("account").getAsString())
                    || !WynncraftClassMenu.matchesTitle(evidence.get("title").getAsString())) return Optional.empty();
            var slots = evidence.getAsJsonArray("slots");
            if (slots.size() > WynncraftClassMenu.MENU_SIZE) return Optional.empty();
            var items = new ArrayList<>(Collections.nCopies(WynncraftClassMenu.MENU_SIZE, ItemStack.EMPTY));
            var seen = new HashSet<Integer>();
            for (var value : slots) {
                var slot = value.getAsJsonObject();
                var number = slot.get("slot");
                int index = number.getAsInt();
                if (index < 0 || index >= items.size() || number.getAsDouble() != index || !seen.add(index)) return Optional.empty();
                var item = ItemStack.CODEC.parse(ops, slot.get("item")).result();
                if (item.isEmpty()) return Optional.empty();
                items.set(index, item.get());
            }
            return WynncraftClassMenu.decode(evidence.get("title").getAsString(), items);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
}
