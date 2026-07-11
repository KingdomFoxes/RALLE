package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.Setting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.Locale;

public final class RalleSettingsScreen extends BaseOwoScreen<FlowLayout> {
    private static final int CONTENT_WIDTH = 410;
    private static final Color DESCRIPTION_COLOR = Color.ofRgb(0xA0A0A0);

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
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var panel = UIContainers.verticalFlow(Sizing.fixed(CONTENT_WIDTH + 32), Sizing.fill(90));
        panel.gap(8)
                .padding(Insets.of(14))
                .surface(Surface.DARK_PANEL)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        panel.child(UIComponents.label(Component.translatable("ralle.settings.title")).shadow(true));

        var search = UIComponents.textBox(Sizing.fixed(CONTENT_WIDTH));
        search.setHint(Component.translatable("ralle.settings.search"));
        panel.child(search);

        var entries = UIContainers.verticalFlow(Sizing.fixed(CONTENT_WIDTH), Sizing.content());
        entries.gap(10);
        rebuildEntries(entries, "");

        var scroll = UIContainers.verticalScroll(Sizing.fixed(CONTENT_WIDTH + 8), Sizing.fill(100), entries);
        scroll.scrollbarThiccness(4).scrollStep(24);
        panel.child(scroll);

        var done = UIComponents.button(Component.translatable("gui.done"), button -> onClose());
        done.horizontalSizing(Sizing.fixed(120));
        panel.child(done);

        search.onChanged().subscribe(query -> rebuildEntries(entries, query));
        root.child(panel);
    }

    private void rebuildEntries(FlowLayout entries, String rawQuery) {
        entries.clearChildren();
        var query = rawQuery.strip().toLowerCase(Locale.ROOT);
        var visibleSettings = 0;

        for (var category : settings.categories()) {
            var categoryMatches = matches(category, query);
            var categoryPanel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            categoryPanel.gap(5).padding(Insets.of(10)).surface(Surface.PANEL);

            for (var setting : category.settings()) {
                if (!categoryMatches && !matches(setting, query)) continue;
                categoryPanel.child(settingRow(setting));
                visibleSettings++;
            }

            if (categoryPanel.children().isEmpty()) continue;

            categoryPanel.child(0, UIComponents.label(category.description())
                    .color(DESCRIPTION_COLOR)
                    .maxWidth(CONTENT_WIDTH - 20));
            categoryPanel.child(0, UIComponents.label(category.title()).shadow(true));
            entries.child(categoryPanel);
        }

        if (visibleSettings == 0) {
            entries.child(UIComponents.label(Component.translatable("ralle.settings.no-results", rawQuery))
                    .color(DESCRIPTION_COLOR)
                    .maxWidth(CONTENT_WIDTH));
        }
    }

    private FlowLayout settingRow(Setting<?> setting) {
        var row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        row.gap(2).padding(Insets.vertical(3));

        if (setting instanceof BooleanSetting booleanSetting) {
            row.child(UIComponents.checkbox(booleanSetting.title())
                    .checked(booleanSetting.value())
                    .onChanged(booleanSetting::set));
        } else if (setting instanceof ChoiceSetting choiceSetting) {
            var button = UIComponents.button(choiceLabel(choiceSetting), ignored -> {});
            button.onPress(pressed -> {
                choiceSetting.set(choiceSetting.nextValue());
                pressed.setMessage(choiceLabel(choiceSetting));
            });
            button.horizontalSizing(Sizing.fill(100));
            row.child(button);
        }

        row.child(UIComponents.label(setting.description())
                .color(DESCRIPTION_COLOR)
                .maxWidth(CONTENT_WIDTH - 20));
        return row;
    }

    private Component choiceLabel(ChoiceSetting setting) {
        return Component.translatable(
                "ralle.settings.choice",
                setting.title(),
                Component.translatable("ralle.settings.value." + setting.value())
        );
    }

    private boolean matches(SettingsCategory category, String query) {
        return query.isEmpty()
                || contains(category.id(), query)
                || contains(category.title().getString(), query)
                || contains(category.description().getString(), query);
    }

    private boolean matches(Setting<?> setting, String query) {
        return query.isEmpty()
                || contains(setting.id(), query)
                || contains(setting.title().getString(), query)
                || contains(setting.description().getString(), query);
    }

    private boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
