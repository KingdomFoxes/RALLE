package org.kingdomfoxes.ralle.war.hqdistance;

import net.minecraft.client.gui.GuiGraphics;

public interface TerritoryLabelRenderer {
    void render(GuiGraphics graphics, HqInspection inspection, TerritoryRenderBounds bounds, boolean headquarters);
}
