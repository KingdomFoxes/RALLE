package org.kingdomfoxes.ralle.classes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Naturally received load evidence. Never queries a menu, guesses a UUID or sends input. */
public final class ClassLoadSignals {
    private ClassLoadSignals() { }

    public static boolean welcome(String text) {
        if (text.length() > 16_384) return false;
        String plain = text.replaceAll("§.", "");
        return plain.lines().map(line -> line.codePoints()
                .filter(cp -> Character.getType(cp) != Character.PRIVATE_USE && !Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip())
                .anyMatch("Welcome to Wynncraft!"::equals)
                && plain.contains("play.wynncraft.com") && plain.contains("wynncraft.com");
    }

    /** The compass carries an eight-character partial ID, not a full character UUID.
     * It may recover only a locally observed selection/load association. */
    public static String compassToken(ItemStack item) {
        if (!item.is(Items.COMPASS)) return null;
        var lore = item.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;
        String token = lore.lines().getFirst().getString();
        return validToken(token) ? token : null;
    }

    public static boolean validToken(String token) { return token != null && token.matches("[a-z0-9]{8}"); }
}
