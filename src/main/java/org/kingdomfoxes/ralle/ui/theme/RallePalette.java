package org.kingdomfoxes.ralle.ui.theme;

import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.util.function.Supplier;

/** Runtime palette roles for RALLE-owned presentation. Alpha and geometry remain consumer-owned. */
public final class RallePalette {
    private static Supplier<String> selectedId = () -> RalleThemeCatalog.DEFAULT_ID;
    private RallePalette() {}

    public static void bind(org.kingdomfoxes.ralle.api.settings.SettingsRegistry settings) {
        ChoiceSetting choice = settings.setting(RalleSettings.UI_THEME_ID, ChoiceSetting.class);
        selectedId = choice::value;
    }
    public static RalleThemeCatalog.Theme theme() { return RalleThemeCatalog.get(selectedId.get()); }
    public static long revision() { return selectedId.get().hashCode(); }
    public static int background() { return theme().background(); }
    public static int outline() { return theme().outline(); }
    public static int accent() { return theme().accent(); }
    public static int frameArgb() { return 0xFF000000 | outline(); }
    public static int accentArgb() { return 0xFF000000 | accent(); }
    public static int darkAccentArgb() { return background() == 0x041330 ? 0xFFB8832F : 0xFF000000 | shade(accent(), .68); }
    public static int insetAccentArgb() { return background() == 0x041330 ? 0xFF35445F : 0xFF000000 | shade(outline(), .55); }
    public static int rowOutlineArgb() { return background() == 0x041330 ? 0xFF3B4354 : 0xFF000000 | shade(outline(), .42); }
    public static int panelArgb() { return background() == 0x041330 ? 0xF20A1830 : alpha(blend(background(), 0, .58), 0xF2); }
    public static int rowArgb() { return background() == 0x041330 ? 0xD91A1E27 : alpha(blend(background(), 0x1A1E27, .50), 0xD9); }
    // These are drawn directly by GuiGraphics as ARGB. RALLE panels and controls
    // darken the submitted background, so their ordinary copy remains light.
    public static int surfaceText() { return 0xFFFFFFFF; }
    public static int primaryText() { return 0xFFFFFFFF; }
    public static int secondary() { return theme().background() == 0x041330 ? 0xFFA9B0BE : 0xFFD8DEE6; }
    public static int secondaryText() { return secondary(); }
    public static int selectionFill() { return alpha(background(), 0xE6); }
    public static int selectionGoldArgb() { return background() == 0x041330 ? 0xFFFFC83D : accentArgb(); }
    public static int confirmationFill() { return 0xFF000000 | background(); }
    public static int hoverArgb() { return alpha(blend(background(), accent(), .22), 0xB8); }
    public static int secondarySurface() { return blend(background(), 0xFFFFFF, .12); }
    public static int notificationSurface() { return background() == 0x041330 ? 0xF20A1830 : alpha(blend(background(), 0, .25), 0xF2); }
    public static int notificationOutline() { return background() == 0x041330 ? 0xFF586985 : 0xFF000000 | shade(outline(), .28); }
    public static int slotSurface() { return background() == 0x041330 ? 0xFF061126 : 0xFF000000 | shade(background(), .62); }
    public static int editorSelectionFill() { return background() == 0x041330 ? 0x88243A55 : alpha(blend(background(), 0, .48), 0x88); }
    public static int editorOuterFrame() { return background() == 0x041330 ? 0xFFE5B94C : accentArgb(); }

    public static String idForName(String name) {
        return RalleThemeCatalog.themes().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow().id();
    }
    public static String key(String id) { return "ralle.settings.theme." + id; }
    public static String labelKey(String id) { return "ralle.settings.value.theme." + id; }
    public static String labelKeyForChoice(String choice) { return labelKey(choice); }
    public static String formattedLabel(String id) {
        var theme = RalleThemeCatalog.get(id);
        String translated = net.minecraft.network.chat.Component.translatable(labelKey(id)).getString();
        return theme.contributor() == null ? translated : translated + " (" + theme.contributor() + ")";
    }
    private static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }
    private static int blend(int a, int b, double amount) {
        int r = (int)(((a >> 16 & 255) * (1-amount)) + ((b >> 16 & 255) * amount));
        int g = (int)(((a >> 8 & 255) * (1-amount)) + ((b >> 8 & 255) * amount));
        int bl = (int)(((a & 255) * (1-amount)) + ((b & 255) * amount));
        return r << 16 | g << 8 | bl;
    }
    private static int shade(int rgb, double amount) { return blend(rgb, 0x000000, amount); }
}
