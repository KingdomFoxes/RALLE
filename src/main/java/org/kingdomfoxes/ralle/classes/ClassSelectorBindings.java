package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.settings.*;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.ui.owo.ClassSelectorWheelScreen;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarState;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Registered local modifier, shared raw/tick input ownership and style routing. No LFG service. */
public final class ClassSelectorBindings {
    public record Entry(ClassShortcut shortcut, ClassCharacter character) {
        public String id() { return shortcut.characterId(); }
        public String name() { return character == null ? shortcut.name().isBlank()
                ? Component.translatable("ralle.classes.missing").getString() : shortcut.name() : character.displayName(shortcut.name()); }
    }
    private final Minecraft minecraft;
    private final SettingsRegistry settings;
    private final ClassSelectorStore store;
    private final KeybindSetting modifier;
    private final KeyMapping mapping;
    private final LfgActionBarState feedback;
    private final ClassSwitcher switcher;
    private final ClassModifierState hold = new ClassModifierState();
    private final ClassSelectorInput input = new ClassSelectorInput();
    private final Map<String, Boolean> physical = new HashMap<>();
    private String applied;
    private List<Entry> entries = List.of();
    private List<String> reserved = List.of();
    private String style;
    private UUID account;
    private long revision;
    private ClassSelectorWheelScreen screen;
    private int selectedIndex = -1;
    private boolean submitted;

    public ClassSelectorBindings(Minecraft minecraft, SettingsRegistry settings, ClassSelectorStore store,
                                 LfgActionBarState feedback, ClassSwitcher switcher) {
        this.minecraft = minecraft;
        this.settings = settings;
        this.store = store;
        this.feedback = feedback;
        this.switcher = switcher;
        modifier = settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class);
        mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ralle.class-modifier",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ralle", "wheels"))));
    }
    public List<Entry> entries() { return entries; }
    public int selectedIndex() { return selectedIndex; }
    public boolean completed() { return input.finished(); }
    public boolean active() { return hold.active() || switcher.active(); }
    public boolean unavailable(Entry entry) { return entry.character() == null || switcher.isCurrent(entry.id()); }
    public boolean current(Entry entry) { return switcher.isCurrent(entry.id()); }
    public boolean optionConflict(Entry entry) {
        return ClassSelectorInput.conflict(entry.shortcut().binding(), modifier.value(),
                entries.stream().map(Entry::shortcut).toList(), reserved);
    }
    public boolean modifierHeld() { return down(modifier.value()); }
    public boolean conflicted() { return ClassBindingConflicts.modifier(settings); }

    public static String synchronizedValue(String setting, String applied, String vanilla) {
        if (InputConstants.UNKNOWN.getName().equals(vanilla)) vanilla = KeybindSetting.UNBOUND;
        if (applied == null) return KeybindSetting.UNBOUND.equals(setting) ? vanilla : setting;
        return !setting.equals(applied) ? setting : vanilla;
    }
    private void synchronize() {
        String value = synchronizedValue(modifier.value(), applied, mapping.saveString());
        if (value.equals(applied)) return;
        // Do not treat the first fresh press of a newly assigned key as a cancelled hold.
        if (hold.active() || screen != null) close();
        else hold.close(false);
        feedback.clear();
        if (!value.equals(modifier.value())) modifier.set(value);
        mapping.setKey(key(value));
        applied = value;
        KeyMapping.resetMapping();
    }

    public void tick() {
        if (switcher.active()) return; // Modifier release after submission must not cancel the operation.
        if (submitted) { submitted = false; close(); }
        synchronize();
        while (mapping.consumeClick()) { }
        boolean held = modifierHeld();
        boolean allowed = allowed();
        if (hold.active() && (!allowed || !held || changed())) close();
        if (hold.observe(held, allowed && !conflicted())) begin();
        if (!hold.active()) return;
        for (String binding : physical.keySet().toArray(String[]::new)) {
            boolean now = down(binding);
            if (now != physical.get(binding)) option(binding, now ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
        }
    }

    private boolean allowed() {
        return settings.setting(RalleSettings.CLASS_SELECTOR_ID, BooleanSetting.class).value()
                && minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null
                && minecraft.getCurrentServer() != null && WynncraftHost.matches(minecraft.getCurrentServer().ip)
                && GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_FALSE
                && (minecraft.screen == null || minecraft.screen == screen && screen != null);
    }
    private List<String> reserved() {
        return ClassBindingConflicts.reserved(settings);
    }
    private boolean changed() {
        return !minecraft.getUser().getProfileId().equals(account) || store.revision() != revision
                || !settings.setting(RalleSettings.CLASS_STYLE_ID, ChoiceSetting.class).value().equals(style)
                || !reserved().equals(reserved);
    }
    private void begin() {
        account = minecraft.getUser().getProfileId();
        var snapshot = store.account(account);
        entries = snapshot.shortcuts().stream().map(s -> new Entry(s, snapshot.character(s.characterId()))).toList();
        reserved = reserved();
        style = settings.setting(RalleSettings.CLASS_STYLE_ID, ChoiceSetting.class).value();
        revision = store.revision();
        selectedIndex = -1;
        physical.clear();
        for (var entry : entries) if (!ClassShortcut.UNBOUND.equals(entry.shortcut().binding()))
            physical.put(entry.shortcut().binding(), down(entry.shortcut().binding()));
        input.begin(physical.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList());
        if (!"no-view".equals(style)) {
            screen = new ClassSelectorWheelScreen(this, style);
            minecraft.setScreen(screen);
        } else feedback.hold(Component.translatable(entries.isEmpty() ? "ralle.classes.wheel.empty"
                : entries.stream().noneMatch(e -> !unavailable(e) && !ClassShortcut.UNBOUND.equals(e.shortcut().binding()) && !optionConflict(e))
                ? "ralle.classes.wheel.no-bound-options" : "ralle.classes.wheel.hold").getString(), LfgActionBarState.Tone.MUTED);
    }

    public boolean handleKeyboard(KeyEvent event, int action) {
        if (switcher.handleKeyboard(event, action)) return true;
        synchronize();
        if (action == GLFW.GLFW_RELEASE && mapping.matches(event)) {
            boolean owned = hold.active();
            close();
            // Raw release must unblock the next press even when both occur between client ticks.
            hold.observe(false, false);
            return owned;
        }
        if (!hold.active()) {
            if (action != GLFW.GLFW_PRESS || !mapping.matches(event) || !allowed()) return false;
            if (conflicted()) { show("ralle.classes.modifier-conflict", true); return true; }
            if (!hold.observe(modifierHeld(), true)) return false;
            begin();
            return true;
        }
        if (!allowed() || changed()) { close(); return false; }
        boolean graphical = screen != null;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) { close(); return true; }
        if (mapping.matches(event)) return true;
        String binding = InputConstants.getKey(event).getName();
        if (physical.containsKey(binding)) { option(binding, action); return true; }
        return graphical;
    }

    private void option(String binding, int action) {
        if (action == GLFW.GLFW_REPEAT) return;
        physical.put(binding, action == GLFW.GLFW_PRESS);
        if (input.finished()) return;
        var shortcuts = entries.stream().map(Entry::shortcut).toList();
        if (ClassSelectorInput.conflict(binding, modifier.value(), shortcuts, reserved)) {
            if (action == GLFW.GLFW_PRESS) show("ralle.classes.conflict", true);
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            var entry = entries.stream().filter(e -> e.shortcut().binding().equals(binding)).findFirst().orElse(null);
            input.press(binding, entry == null || unavailable(entry) ? null : entry.id());
            selectedIndex = index(input.selected());
            if (screen != null) screen.keyboardSelection(selectedIndex);
            else if (selectedIndex >= 0) feedback.hold(entries.get(selectedIndex).name(), LfgActionBarState.Tone.ACCENT);
            else show(entry != null && switcher.isCurrent(entry.id()) ? "ralle.classes.switch.current" : "ralle.classes.missing", true);
        } else if (action == GLFW.GLFW_RELEASE) {
            String execution = input.release(binding, modifierHeld());
            if (execution != null) submit(execution);
        }
    }
    private int index(String identity) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(identity)) return i;
        return -1;
    }
    public void click(int index, boolean fresh) {
        if (!hold.active() || !modifierHeld() || changed() || index < 0 || index >= entries.size()) return;
        if (unavailable(entries.get(index))) {
            show(switcher.isCurrent(entries.get(index).id()) ? "ralle.classes.switch.current" : "ralle.classes.missing-hint", true);
            return;
        }
        String selected = input.click(entries.get(index).id(), fresh);
        if (selected != null) { selectedIndex = index; submit(selected); }
    }
    private void submit(String identity) {
        if (index(identity) < 0) return;
        if (screen != null) screen.submitted(index(identity));
        submitted = switcher.begin(identity);
        if (!submitted) show("ralle.classes.switch.unavailable", true);
    }
    private void show(String key, boolean failure) {
        if (screen != null) screen.feedback(Component.translatable(key), failure);
        feedback.show(Component.translatable(key).getString(), failure ? LfgActionBarState.Tone.DANGER : LfgActionBarState.Tone.MUTED, 3500);
    }
    public void close() {
        hold.close(modifierHeld());
        input.cancel();
        selectedIndex = -1;
        physical.clear();
        feedback.release();
        var old = screen;
        screen = null;
        if (old != null && minecraft.screen == old) minecraft.setScreen(null);
    }
    public void screenRemoved(ClassSelectorWheelScreen removed) {
        if (screen == removed) { screen = null; if (!switcher.active()) close(); }
    }
    public void reset() { switcher.reset(); submitted = false; close(); feedback.clear(); }
    private boolean down(String binding) {
        var key = key(binding);
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                && InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
    }
    private static InputConstants.Key key(String binding) {
        if (KeybindSetting.UNBOUND.equals(binding)) return InputConstants.UNKNOWN;
        try { return InputConstants.getKey(binding); }
        catch (IllegalArgumentException ignored) { return InputConstants.UNKNOWN; }
    }
}
