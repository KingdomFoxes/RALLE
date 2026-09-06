package org.kingdomfoxes.ralle.war.hqdistance;

import com.wynntils.core.text.StyledText;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.render.FontRenderer;
import com.wynntils.utils.render.type.HorizontalAlignment;
import com.wynntils.utils.render.type.TextShadow;
import com.wynntils.utils.render.type.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphics;

public final class MinecraftTerritoryLabelRenderer implements TerritoryLabelRenderer {
    @Override
    public void render(GuiGraphics graphics, HqInspection inspection, TerritoryRenderBounds bounds, boolean headquarters) {
        var font = FontRenderer.getInstance().getFont();
        var layout = TerritoryLabelLayout.calculate(
                bounds,
                font.width(inspection.upperLabel()),
                font.width(inspection.lowerLabel()),
                font.lineHeight,
                headquarters,
                inspection.hasLowerLabel());
        if (layout.isEmpty()) return;

        drawOutlined(
                graphics,
                inspection.upperLabel(),
                layout.get().centerX(),
                layout.get().upperY(), CustomColor.fromARGBInt(inspection.upperColor()));
        if (inspection.hasLowerLabel()) {
            drawOutlined(
                    graphics,
                    inspection.lowerLabel(),
                    layout.get().centerX(),
                    layout.get().lowerY(), CommonColors.WHITE);
        }
    }

    private static void drawOutlined(GuiGraphics graphics, String text, int centerX, int y, CustomColor color) {
        var renderer = FontRenderer.getInstance();
        int x = Math.round(centerX - renderer.getFont().width(text) / 2.0F);
        renderer.renderText(graphics, StyledText.fromString(text), x, y, color,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP, TextShadow.OUTLINE);
    }
}
