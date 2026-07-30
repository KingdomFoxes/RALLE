package org.kingdomfoxes.ralle.ui.owo;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.kingdomfoxes.ralle.RalleClient;

/** Fixed, keybind-only action feedback rendered just above the crosshair. */
public final class LfgActionBarOverlay {
    private static final float TEXT_SCALE = 1.35F;
    private static final int ITEM_GAP = 4;
    private static final int ITEM_SIZE = 16;

    private final Minecraft minecraft;
    private final LfgActionBarState state;

    public LfgActionBarOverlay(Minecraft minecraft, LfgActionBarState state) {
        this.minecraft = minecraft;
        this.state = state;
    }

    public void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, "lfg_action_bar"),
                (graphics, tickCounter) -> {
                    if (minecraft.screen == null) render(graphics);
                }
        );
    }

    private void render(GuiGraphics graphics) {
        var snapshot = state.snapshot();
        if (!snapshot.visible()) return;
        int color = withOpacity(color(snapshot.tone()), snapshot.opacity());
        int y = graphics.guiHeight() / 2 - 34;

        if (snapshot.raid() == null) {
            var text = actionText(snapshot.glyph(), snapshot.text());
            int width = Math.round(minecraft.font.width(text) * TEXT_SCALE);
            drawScaledText(graphics, text, (graphics.guiWidth() - width) / 2, y, color);
            return;
        }

        var prefix = actionText(snapshot.glyph(), snapshot.prefix());
        var raid = RalleTypography.body(Component.literal(snapshot.raidLabel()));
        int prefixWidth = Math.round(minecraft.font.width(prefix) * TEXT_SCALE);
        int raidWidth = Math.round(minecraft.font.width(raid) * TEXT_SCALE);
        int totalWidth = prefixWidth + ITEM_SIZE + ITEM_GAP + raidWidth;
        int x = (graphics.guiWidth() - totalWidth) / 2;
        drawScaledText(graphics, prefix, x, y, color);
        int itemX = x + prefixWidth;
        if (snapshot.opacity() > 0.08d) {
            graphics.renderItem(new ItemStack(RaidPresentation.item(snapshot.raid())), itemX, y - 2);
        }
        drawScaledText(graphics, raid, itemX + ITEM_SIZE + ITEM_GAP, y, color);
    }

    private static Component actionText(LfgActionGlyph glyph, String text) {
        return glyph == null ? RalleTypography.body(Component.literal(text)) : glyph.label(text);
    }

    private void drawScaledText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.drawString(minecraft.font, text, 0, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static int color(LfgActionBarState.Tone tone) {
        return switch (tone) {
            case NORMAL -> 0xFFFFFFFF;
            case ACCENT -> 0xFFF2B84B;
            case MUTED -> 0xFFA9B0BE;
            case DANGER -> 0xFFFF6B6B;
            case POSITIVE -> 0xFF67D391;
        };
    }

    private static int withOpacity(int color, double opacity) {
        int alpha = (int) Math.round(((color >>> 24) & 0xFF) * Math.clamp(opacity, 0d, 1d));
        return alpha << 24 | color & 0x00FFFFFF;
    }
}
