package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

/** Local RGB/rainbow style. Legacy #RRGGBB values remain solid; rainbow appends ;rainbow. */
public final class ColorSetting extends Setting<HighlightStyle> {
    public ColorSetting(String id, Component title, Component description, int rgb) {
        super(id, title, description, new HighlightStyle(rgb, false));
    }

    @Override public String serialize() {
        return value().hex() + (value().rainbow() ? ";rainbow" : "");
    }

    @Override protected HighlightStyle deserialize(String value) {
        boolean rainbow = value.endsWith(";rainbow");
        return HighlightStyle.parse(rainbow ? value.substring(0, value.length() - 8) : value, rainbow);
    }
}
