package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelProvider;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

/** Local, read-only examples; reads cached identity and never authenticates or selects a style. */
final class NameplatePreviewPanelProvider implements CustomSettingsPanelProvider<OwoCustomSettingsPanelContext, UIComponent> {
    @Override public String id() { return "nameplate-preview"; }

    @Override public UIComponent render(OwoCustomSettingsPanelContext context) {
        var panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        panel.gap(10).padding(Insets.of(10)).surface(RalleSurfaces.FRAMED_NAVY);
        panel.child(new Example(false));
        panel.child(new Example(true));
        return panel;
    }

    private static final class Example extends BaseUIComponent {
        private final boolean notification;

        Example(boolean notification) {
            this.notification = notification;
            sizing(Sizing.fill(100), Sizing.fixed(notification ? 64 : 52));
            tooltip(RalleTheme.ui(Component.translatable("ralle.settings.option.nameplate-preview.title")));
        }

        @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var minecraft = Minecraft.getInstance();
            var account = minecraft.getUser().getProfileId();
            var identity = RalleClient.context().cosmetics().cached(account);
            var style = identity != null && identity.hasNameplateAccess()
                    ? identity.selectedStyle() : NameplateStyle.CATALOG.getFirst();
            var settings = RalleClient.context().settings();
            var appearance = style == null ? null : CosmeticAppearance.from(settings, style);
            var username = RalleTheme.ui(Component.literal(minecraft.getUser().getName()));
            var star = RalleTheme.ui(Component.literal("★ "));
            int logicalWidth = notification ? 20 : Math.max(130, minecraft.font.width(username) + minecraft.font.width(star) + 28);
            float scale = Math.min(notification ? 3f : 2f, (float) width / logicalWidth);
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(x + (width - logicalWidth * scale) / 2, y);
                graphics.pose().scale(scale, scale);
                if (!notification && appearance != null && appearance.treatment() == CosmeticAppearance.Treatment.PLATE)
                    LiquidMaterialPresentation.plate(graphics, style, appearance, 0, 0, logicalWidth, 22);
                int headX = notification ? 0 : 2, headY = notification ? 0 : 2;
                int size = notification ? 20 : 18;
                var skin = minecraft.player == null ? DefaultPlayerSkin.get(account) : minecraft.player.getSkin();
                PlayerHeadPresentation.draw(graphics, skin, headX, headY, size, 0xffd9b248,
                        notification ? style : null, notification ? appearance : null);
                if (!notification)
                    LiquidNameComponent.drawName(graphics, star, username, style, appearance, 26, (22 - minecraft.font.lineHeight) / 2);
            } finally { graphics.pose().popMatrix(); }
        }
    }
}
