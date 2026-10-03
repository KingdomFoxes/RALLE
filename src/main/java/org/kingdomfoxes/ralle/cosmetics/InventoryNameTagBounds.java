package org.kingdomfoxes.ralle.cosmetics;

/** Symmetric viewport growth retains the inventory model's center, scale, and mouse-follow pose. */
public record InventoryNameTagBounds(int left, int top, int right, int bottom) {
    public InventoryNameTagBounds fit(int nameWidth, float modelScale, int screenWidth, int screenHeight) {
        int labelWidth = (int) Math.ceil((Math.max(0, nameWidth) + 2) * modelScale * 0.025F) + 4;
        int horizontal = Math.max(0, (labelWidth - (right - left) + 1) / 2);
        horizontal = Math.min(horizontal, Math.max(0, Math.min(left, screenWidth - right)));
        // Include the vanilla half-block attachment gap and optional below-name score line.
        int vertical = Math.min((int) Math.ceil(modelScale * 0.8F), Math.max(0, Math.min(top, screenHeight - bottom)));
        return new InventoryNameTagBounds(left - horizontal, top - vertical, right + horizontal, bottom + vertical);
    }
}
