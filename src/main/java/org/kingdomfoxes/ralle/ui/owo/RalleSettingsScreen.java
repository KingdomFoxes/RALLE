package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

public final class RalleSettingsScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final SettingsRegistry settings;

    RalleSettingsScreen(Screen parent, SettingsRegistry settings) {
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root
                .surface(io.wispforest.owo.ui.core.Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var panel = UIContainers.verticalFlow(Sizing.fixed(310), Sizing.content());
        panel.gap(10).padding(io.wispforest.owo.ui.core.Insets.of(16));
        panel.child(UIComponents.label(net.minecraft.network.chat.Component.translatable("ralle.settings.title")));

        if (settings.categories().isEmpty()) {
            panel.child(UIComponents.label(net.minecraft.network.chat.Component.translatable("ralle.settings.empty")));
            panel.child(UIComponents.label(net.minecraft.network.chat.Component.translatable("ralle.settings.foundation")));
        } else {
            for (var category : settings.categories()) {
                panel.child(UIComponents.label(category.title()));
                panel.child(UIComponents.label(category.description()));
            }
        }

        panel.child(UIComponents.button(net.minecraft.network.chat.Component.translatable("gui.done"), button -> onClose()));
        root.child(panel);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
