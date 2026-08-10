package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
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
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RalleSettingsScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final SettingsRegistry settings;
    private final ChatLayoutService chatLayout;
    private final SettingsNavigationState navigation;
    private final Map<String, UIComponent> sectionComponents = new LinkedHashMap<>();
    private FlowLayout root;
    private FlowLayout sidebarNavigation;
    private FlowLayout document;
    private RalleScrollContainer scroll;
    private TextBoxComponent searchBox;
    private String selectedCategory;
    private String selectedSubcategory;
    private String query = "";
    private SettingsNavigationState.Snapshot preSearchSnapshot;
    private KeybindSetting capturingKeybind;
    private ButtonComponent capturingButton;
    private double lastSavedScroll = -1;
    private FlowLayout finalSection;
    private UIComponent trailingSpace;
    private int appliedTrailingSpace = -1;
    private SettingsScreenLayout.Geometry geometry;
    private Double pendingScrollProgress;

    RalleSettingsScreen(
            Screen parent,
            SettingsRegistry settings,
            ChatLayoutService chatLayout,
            SettingsNavigationState navigation
    ) {
        this.parent = parent;
        this.settings = settings;
        this.chatLayout = chatLayout;
        this.navigation = navigation;
        var snapshot = navigation.snapshot();
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
        if (snapshot.about()) {
            renderAbout();
        } else {
            renderCategory(snapshot.categoryId(), snapshot.subcategoryId(), snapshot.scrollProgress(), false);
        }
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
            boolean selected = category.id().equals(selectedCategory);
            entries.add(new NavigationEntry(
                    category.title(), selected && query.isEmpty(), false, ignored -> selectCategory(category)
            ));
            if (!selected || !query.isEmpty()) continue;
            for (var subcategory : category.subcategories()) {
                entries.add(new NavigationEntry(
                        subcategory.title(),
                        subcategory.id().equals(selectedSubcategory),
                        true,
                        ignored -> jumpTo(subcategory)
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
        selectedCategory = null;
        selectedSubcategory = null;
        navigation.showAbout();
        rebuildSidebar();
        renderAbout();
    }

    private void selectCategory(SettingsCategory category) {
        if (!query.isEmpty()) searchBox.text("");
        var first = category.subcategories().getFirst();
        selectedCategory = category.id();
        selectedSubcategory = first.id();
        navigation.showCategory(selectedCategory, selectedSubcategory, 0);
        rebuildSidebar();
        renderCategory(selectedCategory, selectedSubcategory, 0, false);
    }

    private void renderAbout() {
        clearDocumentState();
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
        document.child(new SettingsSectionDivider(Component.translatable("ralle.settings.subcategory.interface")));
        document.child(entryRow(settings.entry("edit-huds").orElseThrow()));
        document.child(entryRow(settings.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class)));
        document.child(fixedSpacer(SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE));
    }

    private void renderCategory(String categoryId, String requestedSubcategory, double progress, boolean jump) {
        var category = category(categoryId);
        if (category == null) {
            selectAbout();
            return;
        }
        selectedCategory = category.id();
        selectedSubcategory = category.subcategories().stream()
                .anyMatch(value -> value.id().equals(requestedSubcategory))
                ? requestedSubcategory : category.subcategories().getFirst().id();
        clearDocumentState();
        document.clearChildren();
        for (var subcategory : category.subcategories()) {
            var section = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            section.gap(6).margins(Insets.top(SettingsScreenLayout.SECTION_TOP_MARGIN));
            var divider = new SettingsSectionDivider(subcategory.title());
            section.child(divider);
            for (var entry : subcategory.entries()) section.child(entryRow(entry));
            sectionComponents.put(subcategory.id(), divider);
            document.child(section);
            finalSection = section;
        }
        trailingSpace = fixedSpacer(SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE);
        document.child(trailingSpace);
        pendingScrollProgress = Math.clamp(progress, 0, 1);
        if (jump) jumpToId(selectedSubcategory);
    }

    private FlowLayout entryRow(SettingsEntry entry) {
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
            var unmet = settings.unmetDependencies(entry.id()).stream()
                    .map(setting -> setting.title().getString()).toList();
            copy.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.settings.requires", String.join(", ", unmet))))
                    .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                    .color(RalleTheme.ACCENT)
                    .maxWidth(SettingsScreenLayout.dependencyDescriptionWidth(geometry.documentWidth())));
        }
        row.child(copy);
        row.child(control(entry, available));
        return row;
    }

    private ButtonComponent control(SettingsEntry entry, boolean available) {
        ButtonComponent button;
        if (entry instanceof BooleanSetting setting) {
            button = UIComponents.button(booleanLabel(setting), pressed -> toggleBoolean(setting, pressed));
            button.renderer(RalleButtonRenderers.selectable(setting::value));
        } else if (entry instanceof ChoiceSetting setting) {
            button = UIComponents.button(choiceLabel(setting), ignored -> openChoice(setting, buttonFor(entry.id())));
            button.renderer(RalleButtonRenderers.neutral());
        } else if (entry instanceof ActionEntry action) {
            button = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.settings.action.open")), ignored -> openAction(action));
            button.renderer(RalleButtonRenderers.neutral());
        } else if (entry instanceof KeybindSetting setting) {
            button = UIComponents.button(keybindLabel(setting), ignored -> beginKeyCapture(setting, buttonFor(entry.id())));
            button.renderer(RalleButtonRenderers.neutral());
        } else {
            throw new IllegalArgumentException("Unknown settings entry type: " + entry.getClass().getName());
        }
        button.id("setting-control-" + entry.id());
        button.sizing(Sizing.fixed(126), Sizing.fixed(20));
        button.active = available;
        if (!available) button.tooltip(RalleTheme.ui(Component.translatable("ralle.settings.unavailable")));
        return button;
    }

    private void toggleBoolean(BooleanSetting setting, ButtonComponent trigger) {
        int triggerViewportOffset = trigger.y() - scroll.y();
        double fallbackProgress = scroll.progress();
        setting.set(!setting.value());
        renderCategory(selectedCategory, selectedSubcategory, fallbackProgress, false);

        // The mounted document lays out synchronously. Finalize its dynamic tail before restoring
        // the replacement control so an intermediate zero-scroll layout is never presented.
        updateTrailingSpace();
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
                                else if (selectedCategory == null) renderAbout();
                                else renderCategory(selectedCategory, selectedSubcategory, progress, false);
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
            capturingKeybind.set(key == InputConstants.UNKNOWN ? KeybindSetting.UNBOUND : key.getName());
            if (capturingButton != null) capturingButton.setMessage(keybindLabel(capturingKeybind));
            capturingKeybind = null;
            capturingButton = null;
            return true;
        }
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

    private void jumpTo(SettingsSubcategory subcategory) {
        selectedSubcategory = subcategory.id();
        jumpToId(subcategory.id());
        persistNavigation();
        rebuildSidebar();
    }

    private void jumpToId(String id) {
        var section = sectionComponents.get(id);
        if (section == null) return;
        int anchorOffset = section.y() - document.y();
        scroll.scrollToOffsetImmediately(SettingsScreenLayout.jumpScrollOffset(
                anchorOffset, scroll.maximumOffset()
        ));
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
            if (restore.about()) {
                selectedCategory = null;
                selectedSubcategory = null;
                renderAbout();
            } else {
                renderCategory(restore.categoryId(), restore.subcategoryId(), restore.scrollProgress(), false);
            }
            rebuildSidebar();
            return;
        }
        renderSearchResults();
        rebuildSidebar();
    }

    private void renderSearchResults() {
        clearDocumentState();
        document.clearChildren();
        scroll.scrollToImmediately(0);
        int matches = 0;
        for (var result : SettingsSearch.find(settings, query)) {
                var category = result.category();
                var subcategory = result.subcategory();
                var matchingEntries = result.entries();
                var group = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
                group.gap(5).padding(Insets.of(7)).surface(RalleSurfaces.NAVY_PANEL);
                group.child(UIComponents.label(RalleTheme.ui(Component.empty().append(category.title()).append(" › ").append(subcategory.title())))
                        .lineHeight(RalleTheme.BODY_LINE_HEIGHT).color(RalleTheme.ACCENT).maxWidth(documentTextWidth()));
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

    @Override
    public void tick() {
        super.tick();
        if (!query.isEmpty() || selectedCategory == null || sectionComponents.isEmpty()) return;
        if (updateTrailingSpace()) return;
        if (pendingScrollProgress != null) {
            scroll.scrollToImmediately(pendingScrollProgress);
            pendingScrollProgress = null;
        }
        var sectionIds = new ArrayList<>(sectionComponents.keySet());
        var anchorOffsets = sectionComponents.values().stream()
                .map(section -> section.y() - document.y())
                .toList();
        int activeIndex = SettingsScreenLayout.activeSection(
                anchorOffsets, (int) Math.round(scroll.offset()), scroll.maximumOffset()
        );
        String active = activeIndex < 0 ? selectedSubcategory : sectionIds.get(activeIndex);
        if (!active.equals(selectedSubcategory)) {
            selectedSubcategory = active;
            rebuildSidebar();
        }
        if (Math.abs(scroll.progress() - lastSavedScroll) > .005) persistNavigation();
    }

    private void persistNavigation() {
        if (selectedCategory == null || selectedSubcategory == null) return;
        lastSavedScroll = scroll == null ? 0 : scroll.progress();
        navigation.showCategory(selectedCategory, selectedSubcategory, lastSavedScroll);
    }

    private SettingsCategory category(String id) {
        return navigableCategories().stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    private java.util.List<SettingsCategory> navigableCategories() {
        return settings.categories().stream().filter(category -> !"about".equals(category.id())).toList();
    }

    private void clearDocumentState() {
        sectionComponents.clear();
        finalSection = null;
        trailingSpace = null;
        appliedTrailingSpace = -1;
        pendingScrollProgress = null;
    }

    private UIComponent fixedSpacer(int height) {
        var spacer = UIComponents.spacer();
        spacer.sizing(Sizing.fill(100), Sizing.fixed(height));
        return spacer;
    }

    private int documentTextWidth() {
        return Math.max(120, geometry.documentWidth() - 24);
    }

    private boolean updateTrailingSpace() {
        if (finalSection == null || trailingSpace == null || sectionComponents.isEmpty()) return false;
        var finalAnchor = sectionComponents.values().stream().reduce((first, second) -> second).orElse(null);
        if (finalAnchor == null || finalSection.height() <= 0) return false;
        int contentAfterAnchor = finalSection.y() + finalSection.height() - finalAnchor.y();
        int desired = SettingsScreenLayout.trailingDocumentSpace(scroll.height(), contentAfterAnchor);
        if (desired == appliedTrailingSpace) return false;
        appliedTrailingSpace = desired;
        trailingSpace.verticalSizing(Sizing.fixed(desired));
        return true;
    }

    private Component booleanLabel(BooleanSetting setting) {
        return RalleTheme.ui(Component.translatable(setting.value() ? "ralle.settings.enabled" : "ralle.settings.disabled"));
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
        persistNavigation();
        minecraft.setScreen(parent);
    }
}
