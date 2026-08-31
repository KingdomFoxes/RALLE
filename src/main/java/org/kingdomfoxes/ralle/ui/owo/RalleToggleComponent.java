package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;

import java.util.function.Consumer;

/** Compact accessible track switch shared by Boolean settings. */
final class RalleToggleComponent extends ButtonComponent {
    private static final int EDGE = 0xFF080D16;
    private static final int TRACK = 0xFF263448;
    private static final int TRACK_HOVERED = 0xFF31455F;
    private static final int TRACK_DISABLED = 0xFF303846;
    private static final int FOCUS = 0xFFF2B84B;

    private final BooleanSetting setting;

    RalleToggleComponent(BooleanSetting setting, Consumer<ButtonComponent> onPress) {
        super(accessibleLabel(setting), onPress);
        this.setting = setting;
    }

    @Override
    public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int trackX = getX();
        int trackY = getY() + (getHeight() - RalleTogglePresentation.TRACK_HEIGHT) / 2;
        int right = trackX + RalleTogglePresentation.TRACK_WIDTH;
        int bottom = trackY + RalleTogglePresentation.TRACK_HEIGHT;
        if (isFocused()) {
            graphics.fill(trackX - 2, trackY - 2, right + 2, bottom + 2, FOCUS);
            graphics.fill(trackX - 1, trackY - 1, right + 1, bottom + 1, EDGE);
        }
        graphics.fill(trackX, trackY, right, bottom, EDGE);
        int face = !active() ? TRACK_DISABLED : isHovered() ? TRACK_HOVERED : TRACK;
        graphics.fill(trackX + 1, trackY + 1, right - 1, bottom - 1, face);

        int thumbX = RalleTogglePresentation.thumbLeft(trackX, RalleTogglePresentation.TRACK_WIDTH, setting.value());
        int thumbY = RalleTogglePresentation.thumbTop(getY(), getHeight());
        RalleButtonRenderers.drawRaw(
                graphics,
                thumbX,
                thumbY,
                RalleTogglePresentation.THUMB_WIDTH,
                RalleTogglePresentation.THUMB_HEIGHT,
                setting.value() ? RalleButtonRenderers.Kind.PRIMARY : RalleButtonRenderers.Kind.DESTRUCTIVE,
                isHovered(),
                active()
        );
    }

    static Component accessibleLabel(BooleanSetting setting) {
        return RalleTheme.ui(Component.empty().append(setting.title()).append(": ").append(
                Component.translatable(RalleTogglePresentation.stateTranslationKey(setting.value()))
        ));
    }
}
