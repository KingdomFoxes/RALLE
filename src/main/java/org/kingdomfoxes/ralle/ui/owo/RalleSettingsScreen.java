package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.kingdomfoxes.ralle.api.settings.ActionEntry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsEntry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsSearch;
import org.kingdomfoxes.ralle.api.settings.SettingsSubcategory;
import org.kingdomfoxes.ralle.api.settings.CustomPanelEntry;
import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelRegistry;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.rank.GuildRankService;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;

public final class RalleSettingsScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final SettingsRegistry settings;
    private final ChatLayoutService chatLayout;
    private final SettingsNavigationState navigation;
    private final GuildRankService guildRanks;
    private final CustomSettingsPanelRegistry<OwoCustomSettingsPanelContext, UIComponent> customPanels;
    private FlowLayout root;
    private FlowLayout sidebarNavigation;
    private FlowLayout document;
    private RalleScrollContainer scroll;
    private TextBoxComponent searchBox;
    private String selectedCategory;
    private String selectedSubcategory;
    private SettingsNavigationState.Page activePage;
    private String query = "";
    private SettingsNavigationState.Snapshot preSearchSnapshot;
    private KeybindSetting capturingKeybind;
    private ButtonComponent capturingButton;
    private double lastSavedScroll = -1;
    private SettingsScreenLayout.Geometry geometry;
    private Double pendingScrollProgress;

    RalleSettingsScreen(
            Screen parent,
            SettingsRegistry settings,
            ChatLayoutService chatLayout,
            SettingsNavigationState navigation,
            GuildRankService guildRanks,
            CustomSettingsPanelRegistry<OwoCustomSettingsPanelContext, UIComponent> customPanels
    ) {
        this.parent = parent;
        this.settings = settings;
        this.chatLayout = chatLayout;
        this.navigation = navigation;
        this.guildRanks = guildRanks;
        this.customPanels = customPanels;
        var snapshot = navigation.snapshot();
        this.activePage = snapshot.page();
        this.selectedCategory = snapshot.categoryId();
        this.selectedSubcategory = snapshot.subcategoryId();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        root.surface(io.wispforest.owo.ui.core.Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        geometry = SettingsScreenLayout.calculate(width, height);
        var panel = UIContainers.verticalFlow(Sizing.fixed(geometry.panelWidth()), Sizing.fixed(geometry.panelHeight()));
        panel.gap(SettingsScreenLayout.PANEL_GAP)
                .padding(Insets.of(SettingsScreenLayout.PANEL_PADDING))
                .surface(RalleSurfaces.FRAMED_NAVY);
        panel.child(RalleHeader.create(Component.literal("RALLE SETTINGS")));

        var body = UIContainers.horizontalFlow(Sizing.fixed(geometry.bodyWidth()), Sizing.fixed(geometry.bodyHeight()));
        body.gap(SettingsScreenLayout.PANEL_GAP);

        var sidebar = UIContainers.verticalFlow(Sizing.fixed(geometry.sidebarWidth()), Sizing.fixed(geometry.bodyHeight()));
        sidebar.gap(SettingsScreenLayout.SIDEBAR_GAP)
                .padding(Insets.of(SettingsScreenLayout.SIDEBAR_PADDING))
                .surface(RalleSurfaces.NAVY_PANEL);
        searchBox = UIComponents.textBox(Sizing.fill(100));
        searchBox.verticalSizing(Sizing.fixed(SettingsScreenLayout.SEARCH_HEIGHT));
        searchBox.setHint(RalleTheme.ui(Component.translatable("ralle.settings.search")));
        searchBox.onChanged().subscribe(this::searchChanged);
        sidebar.child(searchBox);
        sidebarNavigation = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sidebarNavigation.gap(0);
        var sidebarScroll = new RalleScrollContainer(
                Sizing.fill(100), Sizing.fixed(geometry.navigationHeight()), sidebarNavigation
        );
        sidebarScroll.scrollbarThiccness(2).scrollStep(18);
        sidebar.child(sidebarScroll);

        document = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        document.gap(8).padding(Insets.of(SettingsScreenLayout.DOCUMENT_PADDING));
        scroll = new RalleScrollContainer(
                Sizing.fixed(geometry.documentWidth()), Sizing.fixed(geometry.bodyHeight()), document
        );
        scroll.wheelStep(24).scrollbarThiccness(4).surface(RalleSurfaces.NAVY_PANEL);
        body.child(sidebar).child(scroll);
        panel.child(body);
        root.child(panel);

        rebuildSidebar();
        var snapshot = navigation.snapshot();
        renderSnapshot(snapshot);
    }

    private void rebuildSidebar() {
        sidebarNavigation.clearChildren();
        var entries = new ArrayList<NavigationEntry>();
        entries.add(new NavigationEntry(
                Component.translatable("ralle.settings.about"),
                selectedCategory == null && query.isEmpty(),
                false,
                ignored -> selectAbout()
        ));
        for (var category : navigableCategories()) {
            boolean expanded = category.id().equals(selectedCategory);
            boolean selected = expanded && activePage == SettingsNavigationState.Page.CATEGORY;
            entries.add(new NavigationEntry(
                    category.title(), selected && query.isEmpty(), false, ignored -> selectCategory(category)
            ));
            if (!expanded || !query.isEmpty()) continue;
            for (var subcategory : category.subcategories()) {
                entries.add(new NavigationEntry(
                        subcategory.title(),
                        activePage == SettingsNavigationState.Page.SUBCATEGORY
                                && subcategory.id().equals(selectedSubcategory),
                        true,
                        ignored -> selectSubcategory(category, subcategory)
                ));
            }
        }
        var shapes = SettingsNavigationRailGeometry.shapes(entries.stream()
                .map(entry -> entry.subcategory()
                        ? SettingsNavigationRailGeometry.Level.SUBCATEGORY
                        : SettingsNavigationRailGeometry.Level.CATEGORY)
                .toList());
        for (int index = 0; index < entries.size(); index++) {
            sidebarNavigation.child(navigationRow(entries.get(index), shapes.get(index), index + 1 < entries.size()));
        }
    }

    private UIComponent navigationRow(
            NavigationEntry entry,
            SettingsNavigationRailGeometry.Shape shape,
            boolean gapAfter
    ) {
        int visualHeight = entry.subcategory() ? 18 : 20;
        int rowHeight = visualHeight + (gapAfter ? SettingsScreenLayout.NAVIGATION_ROW_GAP : 0);
        int color = entry.selected() ? 0xFFF2B84B : entry.subcategory() ? 0xFFA9B0BE : 0xFFFFFFFF;
        var row = new SettingsNavigationRailComponent(
                Sizing.fill(100), Sizing.fixed(rowHeight), shape, visualHeight
        );
        int buttonLeftMargin = entry.subcategory() ? 8 : 0;
        var button = new NavigationButtonComponent(
                RalleTheme.ui(entry.label()).copy().withColor(color), entry.subcategory(), ignored -> {}
        );
        button.sizing(Sizing.fill(100), Sizing.fixed(visualHeight));
        button.margins(Insets.left(buttonLeftMargin));
        button.textShadow(false).renderer(RalleButtonRenderers.navigation(
                SettingsNavigationRailGeometry.highlightLeftInset(
                        entry.subcategory()
                                ? SettingsNavigationRailGeometry.Level.SUBCATEGORY
                                : SettingsNavigationRailGeometry.Level.CATEGORY,
                        buttonLeftMargin
                )
        ));
        button.onPress(entry.pressed());
        row.child(button);
        return row;
    }

    private record NavigationEntry(
            Component label,
            boolean selected,
            boolean subcategory,
            java.util.function.Consumer<ButtonComponent> pressed
    ) {}

    private void selectAbout() {
        if (!query.isEmpty()) searchBox.text("");
        query = "";
        activePage = SettingsNavigationState.Page.ABOUT;
        selectedCategory = null;
        selectedSubcategory = null;
        navigation.showAbout();
        rebuildSidebar();
        renderAbout();
    }

    private void selectCategory(SettingsCategory category) {
        if (!query.isEmpty()) searchBox.text("");
        activePage = SettingsNavigationState.Page.CATEGORY;
        selectedCategory = category.id();
        selectedSubcategory = null;
        navigation.showCategory(selectedCategory, 0);
        rebuildSidebar();
        renderCategoryPage(category, 0);
    }

    private void selectSubcategory(SettingsCategory category, SettingsSubcategory subcategory) {
        if (!query.isEmpty()) searchBox.text("");
        activePage = SettingsNavigationState.Page.SUBCATEGORY;
        selectedCategory = category.id();
        selectedSubcategory = subcategory.id();
        navigation.showSubcategory(selectedCategory, selectedSubcategory, 0);
        rebuildSidebar();
        renderSubcategoryPage(category, subcategory, 0);
    }

    private void renderAbout() {
        pendingScrollProgress = null;
        document.clearChildren();
        scroll.scrollToImmediately(0);
        int textWidth = documentTextWidth();
        var version = FabricLoader.getInstance().getModContainer("ralle")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("development");
        var identity = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        identity.verticalAlignment(VerticalAlignment.CENTER);
        identity.child(UIComponents.label(RalleTheme.ui(Component.literal("R.A.L.L.E.")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.ACCENT).shadow(false));
        identity.child(UIComponents.label(RalleTheme.ui(Component.literal("Version " + version)))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.MUTED).margins(Insets.left(6)));
        document.child(identity);
        document.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.settings.about.description")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.TEXT).maxWidth(textWidth));
        document.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.settings.about.disabled-notice")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.ACCENT).maxWidth(textWidth));
        var links = width < 540
                ? UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                : UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        links.gap(6);
        var source = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.settings.about.source")), ignored ->
                ConfirmLinkScreen.confirmLinkNow(this, "https://github.com/KingdomFoxes/RALLE"));
        var issues = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.settings.about.issues")), ignored ->
                ConfirmLinkScreen.confirmLinkNow(this, "https://github.com/KingdomFoxes/RALLE/issues"));
        source.sizing(Sizing.fixed(110), Sizing.fixed(20));
        source.renderer(RalleButtonRenderers.neutral());
        issues.sizing(Sizing.fixed(110), Sizing.fixed(20));
        issues.renderer(RalleButtonRenderers.neutral());
        links.child(source).child(issues);
        document.child(links);
        var about = settings.categories().stream().filter(category -> "about".equals(category.id())).findFirst().orElseThrow();
        for (var entry : about.entries()) document.child(entryRow(entry));
        document.child(fixedSpacer(SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE));
    }

    private void renderCategoryPage(SettingsCategory category, double progress) {
        activePage = SettingsNavigationState.Page.CATEGORY;
        selectedCategory = category.id();
        selectedSubcategory = null;
        renderEntryPage(category.title(), category.entries(), progress);
    }

    private void renderSubcategoryPage(SettingsCategory category, SettingsSubcategory subcategory, double progress) {
        activePage = SettingsNavigationState.Page.SUBCATEGORY;
        selectedCategory = category.id();
        selectedSubcategory = subcategory.id();
        renderEntryPage(subcategory.title(), subcategory.entries(), progress);
    }

    private void renderEntryPage(Component title, java.util.List<SettingsEntry> entries, double progress) {
        document.clearChildren();
        document.child(new SettingsSectionDivider(title));
        var visible = SettingsPageContent.visibleEntries(settings, entries);
        if (visible.isEmpty() && !entries.isEmpty()) {
            var unmet = SettingsPageContent.unmetParentTitles(settings, entries);
            document.child(UIComponents.label(RalleTheme.ui(Component.translatable(
                            "ralle.settings.page.requires", String.join(", ", unmet))))
                    .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.ACCENT).maxWidth(documentTextWidth()));
        } else {
            for (var entry : visible) document.child(entryRow(entry));
        }
        document.child(fixedSpacer(SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE));
        pendingScrollProgress = Math.clamp(progress, 0, 1);
    }

    private void renderSnapshot(SettingsNavigationState.Snapshot snapshot) {
        if (snapshot.about()) {
            activePage = SettingsNavigationState.Page.ABOUT;
            selectedCategory = null;
            selectedSubcategory = null;
            renderAbout();
            return;
        }
        var category = category(snapshot.categoryId());
        if (category == null) {
            selectAbout();
            return;
        }
        if (snapshot.categoryPage()) {
            renderCategoryPage(category, snapshot.scrollProgress());
            return;
        }
        var subcategory = category.subcategories().stream()
                .filter(candidate -> candidate.id().equals(snapshot.subcategoryId())).findFirst().orElse(null);
        if (subcategory == null) selectAbout();
        else renderSubcategoryPage(category, subcategory, snapshot.scrollProgress());
    }

    OverlayContainer<UIComponent> showModal(UIComponent content) {
        var overlay = UIContainers.overlay(content).closeOnClick(false);
        root.child(overlay);
        return overlay;
    }

    private FlowLayout entryRow(SettingsEntry entry) {
        if (entry instanceof CustomPanelEntry panel) {
            var wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            wrapper.child(customPanels.render(panel.providerId(),
                    new OwoCustomSettingsPanelContext(this, documentTextWidth())));
            return wrapper;
        }
        boolean available = settings.available(entry.id());
        boolean stacked = width < 540;
        var row = stacked
                ? UIContainers.verticalFlow(Sizing.fill(100), Sizing.content())
                : UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4).padding(Insets.of(5)).surface(RalleSurfaces.NAVY_ROW);
        if (!stacked) row.verticalAlignment(VerticalAlignment.CENTER);

        var copy = UIContainers.verticalFlow(stacked ? Sizing.fill(100) : Sizing.fill(67), Sizing.content());
        copy.gap(2);
        copy.child(UIComponents.label(RalleTheme.ui(entry.title())).lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(available ? RalleTheme.TEXT : RalleTheme.DISABLED));
        copy.child(UIComponents.label(RalleTheme.ui(entry.description())).lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(available ? RalleTheme.MUTED : RalleTheme.DISABLED)
                .maxWidth(SettingsScreenLayout.descriptionWidth(geometry.documentWidth(), stacked)));
        if (!available) {
            var reason = settings.unavailableReason(entry.id()).orElseGet(() -> {
                var unmet = settings.unmetDependencies(entry.id()).stream()
                        .map(setting -> setting.title().getString()).toList();
                return Component.translatable("ralle.settings.requires", String.join(", ", unmet));
            });
            copy.child(UIComponents.label(RalleTheme.ui(reason))
                    .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                    .color(RalleTheme.ACCENT)
                    .maxWidth(SettingsScreenLayout.dependencyDescriptionWidth(geometry.documentWidth())));
        }
        row.child(copy);
        row.child(control(entry, available));
        return row;
    }

    private UIComponent control(SettingsEntry entry, boolean available) {
        if (entry instanceof BooleanSetting setting) {
            var toggle = new RalleToggleComponent(setting, pressed -> toggleBoolean(setting, pressed));
            configureToggle(entry, available, toggle);
            if (RalleSettings.INTERNAL_GUILD_RANKS_ID.equals(setting.id())) {
                var refresh = UIComponents.button(Component.empty(), ignored -> guildRanks.requestRefresh());
                refresh.id("guild-ranks-refresh");
                refresh.sizing(Sizing.fixed(20), Sizing.fixed(20));
                refresh.renderer(RalleButtonRenderers.refresh());
                updateGuildRankRefreshButton(refresh);

                var refreshLane = UIContainers.horizontalFlow(Sizing.fixed(40), Sizing.fixed(20));
                refreshLane.horizontalAlignment(HorizontalAlignment.RIGHT);
                refreshLane.child(refresh);

                var toggleLane = UIContainers.horizontalFlow(Sizing.fixed(83), Sizing.fixed(20));
                toggleLane.child(toggle);
                var controls = UIContainers.horizontalFlow(Sizing.fixed(RalleTogglePresentation.CONTROL_LANE_WIDTH), Sizing.fixed(20));
                controls.gap(3).child(refreshLane).child(toggleLane);
                return controls;
            }
            var toggleLane = UIContainers.horizontalFlow(
                    Sizing.fixed(RalleTogglePresentation.CONTROL_LANE_WIDTH),
                    Sizing.fixed(RalleTogglePresentation.CONTROL_HEIGHT)
            );
            toggleLane.horizontalAlignment(HorizontalAlignment.CENTER);
            toggleLane.child(toggle);
            return toggleLane;
        }

        ButtonComponent button;
        if (entry instanceof org.kingdomfoxes.ralle.api.settings.ColorSetting setting) {
            button = RalleIconButtons.labeledPalette(Component.translatable("ralle.settings.color.edit"),
                    ignored -> ConsumableColorDialogScreen.openQueueColor(this, setting));
            configureControlButton(entry, available, button);
            button.horizontalSizing(Sizing.fixed(126));
            var lane = UIContainers.horizontalFlow(Sizing.fixed(126), Sizing.fixed(20));
            lane.horizontalAlignment(HorizontalAlignment.CENTER);
            lane.child(button);
            return lane;
        } else if (entry instanceof ChoiceSetting setting) {
            button = UIComponents.button(choiceLabel(setting), ignored -> openChoice(setting, buttonFor(entry.id())));
            button.renderer(RalleButtonRenderers.neutral());
        } else if (entry instanceof ActionEntry action) {
            button = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.settings.action.open")), ignored -> openAction(action));
            button.renderer(RalleButtonRenderers.neutral());
        } else if (entry instanceof KeybindSetting setting) {
            button = UIComponents.button(keybindLabel(setting), ignored -> beginKeyCapture(setting, buttonFor(entry.id())));
            button.renderer(RalleButtonRenderers.neutral());
            if (!KeybindSetting.UNBOUND.equals(setting.value())) {
                configureControlButton(entry, available, button);
                button.horizontalSizing(Sizing.fixed(102));
                var reset = UIComponents.button(
                        Component.empty(),
                        ignored -> resetKeybind(setting, button)
                );
                reset.sizing(Sizing.fixed(20), Sizing.fixed(20));
                reset.renderer(RalleButtonRenderers.destructiveX());
                reset.active = available;
                reset.tooltip(RalleTheme.ui(Component.translatable(
                        available ? "ralle.settings.keybind.reset" : "ralle.settings.unavailable")));

                var controls = UIContainers.horizontalFlow(Sizing.fixed(126), Sizing.fixed(20));
                controls.gap(4).child(reset).child(button);
                return controls;
            }
        } else {
            throw new IllegalArgumentException("Unknown settings entry type: " + entry.getClass().getName());
        }
        configureControlButton(entry, available, button);
        return button;
    }

    private void configureToggle(SettingsEntry entry, boolean available, RalleToggleComponent toggle) {
        toggle.id("setting-control-" + entry.id());
        toggle.sizing(Sizing.fixed(RalleTogglePresentation.TRACK_WIDTH), Sizing.fixed(RalleTogglePresentation.CONTROL_HEIGHT));
        toggle.active = available;
    }

    private void configureControlButton(SettingsEntry entry, boolean available, ButtonComponent button) {
        button.id("setting-control-" + entry.id());
        button.sizing(Sizing.fixed(126), Sizing.fixed(20));
        button.active = available;
        if (!available) button.tooltip(RalleTheme.ui(settings.unavailableReason(entry.id())
                .orElseGet(() -> Component.translatable("ralle.settings.unavailable"))));
    }

    private void toggleBoolean(BooleanSetting setting, ButtonComponent trigger) {
        int triggerViewportOffset = trigger.y() - scroll.y();
        double fallbackProgress = scroll.progress();
        setting.set(!setting.value());
        if (query.isEmpty()) rebuildCurrentPage(fallbackProgress);
        else renderSearchResults();
        var replacement = buttonFor(setting.id());
        if (replacement == null) return;

        int replacementDocumentOffset = replacement.y() - document.y();
        scroll.scrollToOffsetImmediately(SettingsScreenLayout.anchoredScrollOffset(
                replacementDocumentOffset, triggerViewportOffset, scroll.maximumOffset()
        ));
        pendingScrollProgress = null;
    }

    private ButtonComponent buttonFor(String id) {
        return root.childById(ButtonComponent.class, "setting-control-" + id);
    }

    private void openChoice(ChoiceSetting setting, ButtonComponent trigger) {
        if (trigger == null) return;
        DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                trigger.x(), trigger.y() + trigger.height(), menu -> {
                    for (var choice : setting.choices()) {
                        menu.button(RalleTheme.ui(Component.translatable("ralle.settings.value." + choice)), dropdown -> {
                            double progress = scroll.progress();
                            setting.set(choice);
                            root.removeChild(dropdown);
                            if (RalleSettings.INTERFACE_FONT_ID.equals(setting.id())) {
                                refreshSearchHintTypography();
                                rebuildSidebar();
                                if (!query.isEmpty()) renderSearchResults();
                                else rebuildCurrentPage(progress);
                            } else {
                                trigger.setMessage(choiceLabel(setting));
                            }
                        });
                    }
                });
    }

    private void beginKeyCapture(KeybindSetting setting, ButtonComponent button) {
        capturingKeybind = setting;
        capturingButton = button;
        if (button != null) button.setMessage(RalleTheme.ui(Component.translatable("ralle.settings.keybind.press-key")));
    }

    private void resetKeybind(KeybindSetting setting, ButtonComponent trigger) {
        updateKeybind(setting, KeybindSetting.UNBOUND, trigger);
    }

    private void updateKeybind(KeybindSetting setting, String value, ButtonComponent trigger) {
        int triggerViewportOffset = trigger.y() - scroll.y();
        double fallbackProgress = scroll.progress();
        setting.set(value);
        if (query.isEmpty()) {
            rebuildCurrentPage(fallbackProgress);
        } else {
            renderSearchResults();
        }

        var replacement = buttonFor(setting.id());
        if (replacement == null) return;
        int replacementDocumentOffset = replacement.y() - document.y();
        scroll.scrollToOffsetImmediately(SettingsScreenLayout.anchoredScrollOffset(
                replacementDocumentOffset, triggerViewportOffset, scroll.maximumOffset()
        ));
        pendingScrollProgress = null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturingKeybind != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                var cancelled = capturingKeybind;
                capturingKeybind = null;
                if (capturingButton != null) capturingButton.setMessage(keybindLabel(cancelled));
                capturingButton = null;
                return true;
            }
            var key = event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE
                    ? InputConstants.UNKNOWN : InputConstants.getKey(event);
            var captured = capturingKeybind;
            var trigger = capturingButton;
            capturingKeybind = null;
            capturingButton = null;
            updateKeybind(captured, key == InputConstants.UNKNOWN ? KeybindSetting.UNBOUND : key.getName(), trigger);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && RalleModalDialogs.dismissTop(root)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (capturingKeybind != null) {
            // Raid LFG bindings are deliberately keyboard-only; keep waiting for a keyboard key.
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private void openAction(ActionEntry action) {
        persistNavigation();
        if ("edit-chat-layout".equals(action.id())) {
            minecraft.setScreen(new ChatLayoutEditorScreen(this, chatLayout));
        } else if ("edit-notification-position".equals(action.id())) {
            minecraft.setScreen(new ChatLayoutEditorScreen(this, chatLayout, LfgNotificationOverlay.ELEMENT_ID));
        } else if ("edit-huds".equals(action.id())) {
            minecraft.setScreen(ChatLayoutEditorScreen.forAllEnabled(this, chatLayout, settings));
        } else {
            throw new IllegalArgumentException("Unknown settings action: " + action.id());
        }
    }

    private void searchChanged(String rawQuery) {
        var next = rawQuery.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty() && !next.isEmpty()) {
            persistNavigation();
            preSearchSnapshot = navigation.snapshot();
        }
        query = next;
        if (query.isEmpty()) {
            var restore = preSearchSnapshot == null ? navigation.snapshot() : preSearchSnapshot;
            preSearchSnapshot = null;
            renderSnapshot(restore);
            rebuildSidebar();
            return;
        }
        renderSearchResults();
        rebuildSidebar();
    }

    private void renderSearchResults() {
        pendingScrollProgress = null;
        document.clearChildren();
        scroll.scrollToImmediately(0);
        int matches = 0;
        for (var result : SettingsSearch.find(settings, query)) {
                var category = result.category();
                var matchingEntries = result.entries();
                var group = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
                group.gap(5).padding(Insets.of(7)).surface(RalleSurfaces.NAVY_PANEL);
                var breadcrumb = result.categoryPage()
                        ? category.title()
                        : Component.empty().append(category.title()).append(" › ").append(result.subcategory().title());
                var openPage = UIComponents.button(RalleTheme.ui(breadcrumb), ignored -> openSearchResult(result));
                openPage.sizing(Sizing.fill(100), Sizing.fixed(20));
                openPage.renderer(RalleButtonRenderers.navigation(0));
                group.child(openPage);
                for (var entry : matchingEntries) group.child(entryRow(entry));
                document.child(group);
                matches += matchingEntries.size();
        }
        if (matches == 0) {
            document.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.settings.no-results", query)))
                    .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.MUTED).maxWidth(documentTextWidth()));
        }
        document.child(fixedSpacer(SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE));
    }

    private void openSearchResult(SettingsSearch.ResultGroup result) {
        if ("about".equals(result.category().id())) {
            selectAbout();
        } else if (result.categoryPage()) {
            selectCategory(result.category());
        } else {
            selectSubcategory(result.category(), result.subcategory());
        }
    }

    private void rebuildCurrentPage(double progress) {
        if (activePage == SettingsNavigationState.Page.ABOUT) {
            renderAbout();
            return;
        }
        var category = category(selectedCategory);
        if (category == null) {
            selectAbout();
        } else if (activePage == SettingsNavigationState.Page.CATEGORY) {
            renderCategoryPage(category, progress);
        } else {
            var subcategory = category.subcategories().stream()
                    .filter(candidate -> candidate.id().equals(selectedSubcategory)).findFirst().orElse(null);
            if (subcategory == null) selectAbout();
            else renderSubcategoryPage(category, subcategory, progress);
        }
    }

    @Override
    public void tick() {
        super.tick();
        var rankRefresh = root.childById(ButtonComponent.class, "guild-ranks-refresh");
        if (rankRefresh != null) updateGuildRankRefreshButton(rankRefresh);
        if (pendingScrollProgress != null) {
            scroll.scrollToImmediately(pendingScrollProgress);
            pendingScrollProgress = null;
        }
        if (!query.isEmpty() || activePage == SettingsNavigationState.Page.ABOUT) return;
        if (Math.abs(scroll.progress() - lastSavedScroll) > .005) persistNavigation();
    }

    private void updateGuildRankRefreshButton(ButtonComponent button) {
        button.active = guildRanks.canRefresh() && !guildRanks.refreshing();
        String tooltip = guildRanks.refreshing()
                ? "ralle.settings.guild-ranks.refreshing"
                : guildRanks.canRefresh()
                        ? "ralle.settings.guild-ranks.refresh"
                        : "ralle.settings.guild-ranks.refresh-unavailable";
        button.tooltip(RalleTheme.ui(Component.translatable(tooltip)));
    }

    private void persistNavigation() {
        if (activePage == SettingsNavigationState.Page.ABOUT || selectedCategory == null) return;
        lastSavedScroll = scroll == null ? 0 : scroll.progress();
        if (activePage == SettingsNavigationState.Page.CATEGORY) {
            navigation.showCategory(selectedCategory, lastSavedScroll);
        } else {
            navigation.showSubcategory(selectedCategory, selectedSubcategory, lastSavedScroll);
        }
    }

    private SettingsCategory category(String id) {
        return navigableCategories().stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    private java.util.List<SettingsCategory> navigableCategories() {
        return settings.categories().stream().filter(category -> !"about".equals(category.id())).toList();
    }

    private UIComponent fixedSpacer(int height) {
        var spacer = UIComponents.spacer();
        spacer.sizing(Sizing.fill(100), Sizing.fixed(height));
        return spacer;
    }

    private int documentTextWidth() {
        return Math.max(120, geometry.documentWidth() - 24);
    }

    private Component choiceLabel(ChoiceSetting setting) {
        return RalleTheme.dropdownLabel(Component.translatable("ralle.settings.value." + setting.value()));
    }

    private void refreshSearchHintTypography() {
        searchBox.setHint(RalleTheme.ui(Component.translatable("ralle.settings.search")));
    }

    private Component keybindLabel(KeybindSetting setting) {
        if (KeybindSetting.UNBOUND.equals(setting.value())) {
            return RalleTheme.ui(Component.translatable("ralle.settings.keybind.unbound"));
        }
        try {
            return RalleTheme.ui(InputConstants.getKey(setting.value()).getDisplayName());
        } catch (IllegalArgumentException ignored) {
            return RalleTheme.ui(Component.translatable("ralle.settings.keybind.unbound"));
        }
    }

    @Override
    public void onClose() {
        if (RalleModalDialogs.dismissTop(root)) return;
        persistNavigation();
        minecraft.setScreen(parent);
    }
}
