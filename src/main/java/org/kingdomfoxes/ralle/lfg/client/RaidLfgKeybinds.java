package org.kingdomfoxes.ralle.lfg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;
import org.kingdomfoxes.ralle.ui.owo.LfgActionGlyph;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarState;
import org.kingdomfoxes.ralle.ui.owo.RaidPresentation;
import org.kingdomfoxes.ralle.ui.owo.RaidLfgScreen;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.UUID;

/** Owns the persisted Raid LFG bindings, chord input, targeting, and keybind-only feedback. */
public final class RaidLfgKeybinds {
    public static final String OPEN_ID = "raid-lfg-keybind";
    public static final String JOIN_ID = "raid-lfg-join-keybind";
    public static final String CLOSE_ID = "raid-lfg-close-keybind";
    public static final String LEAVE_DISBAND_ID = "raid-lfg-leave-disband-keybind";
    public static final String PARTY_FILLED_ID = "raid-lfg-party-filled-keybind";
    public static final String PING_ID = "raid-lfg-ping-keybind";
    public static final String LOCK_ID = "raid-lfg-lock-keybind";
    public static final String CREATE_ID = "raid-lfg-create-keybind";
    public static final String KICK_ID = "raid-lfg-kick-keybind";

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("ralle", "controls")
    );
    private static final LfgProtocol.RaidType[] CREATE_RAIDS = {
            null,
            LfgProtocol.RaidType.DAILIES,
            LfgProtocol.RaidType.NOTG,
            LfgProtocol.RaidType.NOL,
            LfgProtocol.RaidType.TCC,
            LfgProtocol.RaidType.TNA,
            LfgProtocol.RaidType.TWP
    };

    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final RaidLfgService service;
    private final LfgSoundPlayer sounds;
    private final LfgNotificationManager notifications;
    private final HostPartyInviteController hostPartyInvites;
    private final RaidRegionDetector regionDetector;
    private final LfgActionBarState actionBar;
    private final LfgDisbandConfirmation disbandConfirmation;
    private final LfgLockDebouncer lockDebouncer;
    private final EnumMap<Action, Binding> bindings = new EnumMap<>(Action.class);
    private final LfgChordState<Selection> chordState = new LfgChordState<>();

    private ChordMode chordMode = ChordMode.NONE;

    public RaidLfgKeybinds(Minecraft minecraft, SettingsRegistry settings, RaidLfgService service,
                           LfgSoundPlayer sounds, LfgNotificationManager notifications,
                           HostPartyInviteController hostPartyInvites,
                           RaidRegionDetector regionDetector, LfgActionBarState actionBar,
                           LfgDisbandConfirmation disbandConfirmation,
                           LfgLockDebouncer lockDebouncer) {
        this.minecraft = minecraft;
        this.service = service;
        this.sounds = sounds;
        this.notifications = notifications;
        this.hostPartyInvites = hostPartyInvites;
        this.regionDetector = regionDetector;
        this.actionBar = actionBar;
        this.disbandConfirmation = disbandConfirmation;
        this.lockDebouncer = lockDebouncer;
        this.enabled = settings.setting("raid-lfg-enabled", BooleanSetting.class);

        register(settings, Action.OPEN, OPEN_ID, "key.ralle.raid-lfg");
        register(settings, Action.JOIN, JOIN_ID, "key.ralle.raid-lfg-join");
        register(settings, Action.CLOSE, CLOSE_ID, "key.ralle.raid-lfg-close");
        register(settings, Action.LEAVE_DISBAND, LEAVE_DISBAND_ID, "key.ralle.raid-lfg-leave-disband");
        register(settings, Action.PARTY_FILLED, PARTY_FILLED_ID, "key.ralle.raid-lfg-party-filled");
        register(settings, Action.PING, PING_ID, "key.ralle.raid-lfg-ping");
        register(settings, Action.LOCK, LOCK_ID, "key.ralle.raid-lfg-lock");
        register(settings, Action.CREATE, CREATE_ID, "key.ralle.raid-lfg-create");
        register(settings, Action.KICK, KICK_ID, "key.ralle.raid-lfg-kick");
        applyChangedSettings();
    }

    public void tick() {
        applyChangedSettings();
        drainClicks();
        validateConfirmation();
        tickLockDebounce();

        if (!inputAllowed()) {
            resetChord(true);
            return;
        }

        boolean createDown = down(Action.CREATE);
        boolean kickDown = down(Action.KICK);
        if (createDown == kickDown) {
            if (chordMode != ChordMode.NONE) resetChord(false);
            return;
        }

        var nextMode = createDown ? ChordMode.CREATE : ChordMode.KICK;
        beginChord(nextMode);
    }

    /**
     * Called at the head of Minecraft's keyboard handler. Returning true consumes only top-row
     * digits used by an already-active, valid Create/Kick chord.
     */
    public boolean handleKeyboard(KeyEvent event, int glfwAction) {
        if (!inputAllowed()) {
            if (glfwAction == GLFW.GLFW_PRESS
                    && minecraft.screen == null
                    && minecraft.level != null
                    && minecraft.player != null
                    && enabled.value()
                    && matches(Action.OPEN, event)) {
                openRaidLfg();
            }
            return false;
        }

        boolean createModifier = matches(Action.CREATE, event);
        boolean kickModifier = matches(Action.KICK, event);
        if (createModifier != kickModifier) {
            var eventMode = createModifier ? ChordMode.CREATE : ChordMode.KICK;
            if (glfwAction == GLFW.GLFW_PRESS) beginChord(eventMode);
            else if (glfwAction == GLFW.GLFW_RELEASE && chordMode == eventMode) resetChord(false);
            return false;
        }

        int digit = topRowDigit(event.key());
        if (digit != 0 && chordMode != ChordMode.NONE
                && !chordState.completed() && modifierStillDown()) {
            if (chordMode == ChordMode.KICK && (digit < 2 || digit > 4)) {
                return false;
            }
            if (glfwAction == GLFW.GLFW_PRESS) {
                var press = chordState.press(digit, () -> select(digit));
                if (press.accepted() && !press.ambiguous()) {
                    presentSelection(press.selection(), true);
                }
                return true;
            }
            if (glfwAction == GLFW.GLFW_REPEAT) return true;
            if (glfwAction == GLFW.GLFW_RELEASE) {
                var release = chordState.release(digit, modifierStillDown());
                if (!release.matched()) return true;
                if (release.execution() != null) execute(release.execution());
                else actionBar.release();
                return true;
            }
            return true;
        }

        if (glfwAction == GLFW.GLFW_PRESS) {
            for (var action : Action.values()) {
                if (action == Action.CREATE || action == Action.KICK || !matches(action, event)) continue;
                if (action == Action.OPEN) openRaidLfg();
                else executeSimple(action);
                break;
            }
        }
        return false;
    }

    private void beginChord(ChordMode nextMode) {
        if (chordMode == ChordMode.NONE) {
            chordMode = nextMode;
            chordState.begin();
            actionBar.hold(nextMode == ChordMode.CREATE ? "Create" : "Kick",
                    LfgActionBarState.Tone.NORMAL,
                    nextMode == ChordMode.CREATE ? LfgActionGlyph.CREATE : LfgActionGlyph.KICK);
        } else if (chordMode != nextMode) {
            resetChord(false);
        }
    }

    private void drainClicks() {
        for (var binding : bindings.values()) {
            while (binding.mapping.consumeClick()) {
                // Raw GLFW action handling suppresses repeat-driven KeyMapping click counts.
            }
        }
    }

    private void openRaidLfg() {
        minecraft.setScreen(new RaidLfgScreen(
                minecraft.screen, service, regionDetector, sounds, notifications, lockDebouncer));
    }

    private void executeSimple(Action action) {
        switch (action) {
            case JOIN -> notifications.newestJoinableCardId().ifPresent(notifications::join);
            case CLOSE -> notifications.newestVisibleCardId().ifPresent(id -> {
                actionBar.show("Close", LfgActionBarState.Tone.NORMAL);
                notifications.close(id);
            });
            case LEAVE_DISBAND -> leaveOrDisband();
            case PARTY_FILLED -> partyFilled();
            case PING -> ping();
            case LOCK -> lockOrUnlock();
            default -> {}
        }
    }

    private void partyFilled() {
        var lobby = currentLobby();
        var viewer = service.store().state().viewer();
        if (lobby == null || viewer == null) {
            showNoParty();
            return;
        }
        if (!lobby.hostedBy(viewer.minecraftUuid())) {
            showMissingHostParty();
            return;
        }
        if (lobby.members().size() < lobby.capacity()) {
            actionBar.show("Party is not filled", LfgActionBarState.Tone.MUTED);
            return;
        }
        if (hostPartyInvites.inviteAll(lobby.lobbyId())) {
            actionBar.show("Party filled", LfgActionBarState.Tone.ACCENT);
            notifications.close(lobby.lobbyId());
        } else {
            actionBar.show("Party invite unavailable", LfgActionBarState.Tone.DANGER);
        }
    }

    private void leaveOrDisband() {
        var lobby = currentLobby();
        var viewer = service.store().state().viewer();
        if (lobby == null || viewer == null) {
            showNoParty();
            return;
        }
        boolean cardVisible = notifications.hasVisiblePersistentCard(lobby.lobbyId());
        if (!lobby.hostedBy(viewer.minecraftUuid())) {
            if (!cardVisible) {
                actionBar.show("Leave", LfgActionBarState.Tone.DANGER,
                        LfgActionGlyph.LEAVE_DISBAND);
            }
            service.leave(lobby.lobbyId()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
                if (failure == null) sounds.playPartyLeft();
                else if (!cardVisible) {
                    actionBar.show("Leave failed", LfgActionBarState.Tone.DANGER,
                            LfgActionGlyph.LEAVE_DISBAND);
                }
            }));
            return;
        }

        var binding = bindings.get(Action.LEAVE_DISBAND);
        String prompt = "Press [" + binding.mapping.getTranslatedKeyMessage().getString() + "] again to disband";
        var result = disbandConfirmation.request(lobby.lobbyId(), lobby.revision(), prompt);
        if (result == LfgDisbandConfirmation.Result.ARMED) {
            if (!cardVisible) {
                actionBar.show(prompt, LfgActionBarState.Tone.DANGER,
                        LfgDisbandConfirmation.DURATION.toMillis(),
                        LfgActionGlyph.LEAVE_DISBAND);
            }
            return;
        }
        if (!cardVisible) {
            actionBar.show("Disband", LfgActionBarState.Tone.DANGER,
                    LfgActionGlyph.LEAVE_DISBAND);
        }
        service.disband(lobby.lobbyId()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (failure == null) {
                sounds.playPartyLeft();
            } else if (!cardVisible) {
                actionBar.show("Disband failed", LfgActionBarState.Tone.DANGER,
                        LfgActionGlyph.LEAVE_DISBAND);
            }
        }));
    }

    private void ping() {
        var lobby = currentHostLobby();
        if (lobby == null) {
            showMissingHostParty();
            return;
        }
        int cooldownSeconds = service.pingCooldownSeconds(lobby.lobbyId());
        if (cooldownSeconds > 0) {
            actionBar.show(pingCooldownMessage(cooldownSeconds),
                    LfgActionBarState.Tone.MUTED, LfgActionGlyph.PING);
            return;
        }
        actionBar.show("Ping", LfgActionBarState.Tone.NORMAL, LfgActionGlyph.PING);
        service.ping(lobby.lobbyId()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (failure == null) sounds.playLocalPartyPing();
            else actionBar.show("Ping failed", LfgActionBarState.Tone.DANGER, LfgActionGlyph.PING);
        }));
    }

    private void lockOrUnlock() {
        var lobby = currentHostLobby();
        if (lobby == null) {
            showMissingHostParty();
            return;
        }
        lockDebouncer.toggle(lobby, LfgLockDebouncer.Origin.KEYBIND).ifPresent(locked -> {
            sounds.playLockToggle(locked);
            actionBar.show(locked ? "Lock" : "Unlock", LfgActionBarState.Tone.NORMAL,
                    locked ? LfgActionGlyph.LOCK : LfgActionGlyph.UNLOCK);
        });
    }

    private Selection select(int digit) {
        if (chordMode == ChordMode.CREATE) {
            return new Selection(digit, CREATE_RAIDS[digit], null, null, null);
        }
        var lobby = currentHostLobby();
        if (lobby == null || digit < 2 || digit > 4 || digit > lobby.members().size()) {
            return new Selection(digit, null, null, null, lobby == null ? null : lobby.lobbyId());
        }
        var member = lobby.members().get(digit - 1);
        if (member.role() == LfgProtocol.MemberRole.HOST) {
            return new Selection(digit, null, null, null, lobby.lobbyId());
        }
        return new Selection(digit, null, member.minecraftUuid(), member.ign(), lobby.lobbyId());
    }

    private void presentSelection(Selection selected, boolean held) {
        if (chordMode == ChordMode.CREATE) {
            var raid = selected.raid();
            if (held) {
                actionBar.holdRaid("Create + ", raid, RaidPresentation.shortName(raid),
                        LfgActionBarState.Tone.ACCENT, LfgActionGlyph.CREATE);
            } else {
                actionBar.showRaid("Create + ", raid, RaidPresentation.shortName(raid),
                        LfgActionBarState.Tone.ACCENT, LfgActionGlyph.CREATE);
            }
            return;
        }
        if (selected.lobbyId() == null) {
            String message = currentLobby() == null
                    ? "Not in a Raid LFG party" : "Only the party host can do that";
            if (held) actionBar.hold(message, LfgActionBarState.Tone.DANGER, LfgActionGlyph.KICK);
            else actionBar.show(message, LfgActionBarState.Tone.DANGER, LfgActionGlyph.KICK);
            return;
        }
        String target = selected.targetId() == null ? "Empty slot" : selected.targetIgn();
        if (held) {
            actionBar.hold("Kick + " + target,
                    selected.targetId() == null ? LfgActionBarState.Tone.MUTED : LfgActionBarState.Tone.NORMAL,
                    LfgActionGlyph.KICK);
        } else {
            actionBar.show("Kick + " + target,
                    selected.targetId() == null ? LfgActionBarState.Tone.MUTED : LfgActionBarState.Tone.NORMAL,
                    LfgActionGlyph.KICK);
        }
    }

    private void execute(Selection selected) {
        presentSelection(selected, false);
        if (chordMode == ChordMode.CREATE) {
            var state = service.store().state();
            if (state.capabilities() == null || !state.capabilities().create() || service.pendingCreate()) return;
            var region = regionDetector.detect();
            if (region.isEmpty()) {
                actionBar.show("region not detectd idk why REPORT TS", LfgActionBarState.Tone.DANGER);
                return;
            }
            service.create(selected.raid(), region.get(), null)
                    .whenComplete((ignored, failure) -> minecraft.execute(() -> {
                        if (failure == null) sounds.playPartyCreated();
                        else actionBar.show("Create failed", LfgActionBarState.Tone.DANGER,
                                LfgActionGlyph.CREATE);
                    }));
            return;
        }
        if (selected.targetId() == null || selected.lobbyId() == null) return;
        var lobby = currentHostLobby();
        if (lobby == null || !lobby.lobbyId().equals(selected.lobbyId())) return;
        var target = lobby.members().stream()
                .filter(member -> member.minecraftUuid().equals(selected.targetId()))
                .filter(member -> member.role() != LfgProtocol.MemberRole.HOST)
                .findFirst()
                .orElse(null);
        if (target == null) return;
        service.kick(lobby.lobbyId(), target.minecraftUuid(), target.ign())
                .whenComplete((ignored, failure) -> minecraft.execute(() -> {
                    if (failure == null) sounds.playKickSucceeded();
                    else actionBar.show("Kick failed", LfgActionBarState.Tone.DANGER,
                            LfgActionGlyph.KICK);
                }));
    }

    private void tickLockDebounce() {
        var lobby = currentHostLobby();
        boolean online = service.lifecycle() == RaidLfgService.LifecycleState.ONLINE;
        boolean pending = lobby != null && service.pending(lobby.lobbyId(), "lock");
        lockDebouncer.poll(lobby, online, pending).ifPresent(command ->
                service.setLocked(command.lobbyId(), command.locked())
                        .whenComplete((ignored, failure) -> minecraft.execute(() -> {
                            lockDebouncer.complete(command.lobbyId());
                            if (failure != null && command.origin() == LfgLockDebouncer.Origin.KEYBIND) {
                                actionBar.show(command.locked() ? "Lock failed" : "Unlock failed",
                                        LfgActionBarState.Tone.DANGER,
                                        command.locked() ? LfgActionGlyph.LOCK : LfgActionGlyph.UNLOCK);
                            }
                        })));
    }

    private void showMissingHostParty() {
        if (currentLobby() == null) showNoParty();
        else actionBar.show("Only the party host can do that", LfgActionBarState.Tone.DANGER);
    }

    private void showNoParty() {
        actionBar.show("Not in a Raid LFG party", LfgActionBarState.Tone.DANGER);
    }

    private void validateConfirmation() {
        var snapshot = disbandConfirmation.snapshot();
        if (snapshot == null) return;
        var lobby = currentHostLobby();
        boolean keyboardPrompt = snapshot.prompt().startsWith("Press [");
        boolean valid = lobby != null
                && service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && (!keyboardPrompt || inputAllowed());
        disbandConfirmation.validate(
                lobby == null ? new UUID(0, 0) : lobby.lobbyId(),
                lobby == null ? -1 : lobby.revision(),
                valid
        );
    }

    private LfgProtocol.Lobby currentLobby() {
        var viewer = service.store().state().viewer();
        if (viewer == null) return null;
        return service.store().state().lobbyList().stream()
                .filter(lobby -> lobby.contains(viewer.minecraftUuid()))
                .findFirst()
                .orElse(null);
    }

    private LfgProtocol.Lobby currentHostLobby() {
        var viewer = service.store().state().viewer();
        if (viewer == null) return null;
        return service.store().state().lobbyList().stream()
                .filter(lobby -> lobby.hostedBy(viewer.minecraftUuid()))
                .findFirst()
                .orElse(null);
    }

    private boolean inputAllowed() {
        return enabled.value()
                && minecraft.screen == null
                && minecraft.level != null
                && minecraft.player != null
                && service.lifecycle() == RaidLfgService.LifecycleState.ONLINE;
    }

    private boolean modifierStillDown() {
        return chordMode == ChordMode.CREATE ? down(Action.CREATE) : down(Action.KICK);
    }

    private boolean down(Action action) {
        return !conflicted(action) && bindings.get(action).mapping.isDown();
    }

    private boolean matches(Action action, KeyEvent event) {
        return !conflicted(action) && bindings.get(action).mapping.matches(event);
    }

    private boolean conflicted(Action action) {
        var value = bindings.get(action).setting.value();
        if (KeybindSetting.UNBOUND.equals(value)) return false;
        int matches = 0;
        for (var binding : bindings.values()) {
            if (value.equals(binding.setting.value())) matches++;
        }
        return matches > 1;
    }

    private void resetChord(boolean clear) {
        if (chordMode != ChordMode.NONE && !clear) actionBar.release();
        else if (clear) actionBar.clear();
        chordMode = ChordMode.NONE;
        chordState.reset();
    }

    private void register(SettingsRegistry settings, Action action, String id, String translationKey) {
        var setting = settings.setting(id, KeybindSetting.class);
        var mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                translationKey, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY
        ));
        bindings.put(action, new Binding(setting, mapping));
    }

    private void applyChangedSettings() {
        boolean changed = false;
        for (var binding : bindings.values()) {
            String mappingValue = storedValue(binding.mapping.saveString());
            String synchronizedValue = synchronizedValue(
                    binding.setting.value(), binding.appliedValue, mappingValue);
            if (synchronizedValue.equals(binding.appliedValue)) continue;

            if (!synchronizedValue.equals(binding.setting.value())) {
                binding.setting.set(synchronizedValue);
            }
            if (!synchronizedValue.equals(mappingValue)) {
                binding.mapping.setKey(key(synchronizedValue));
            }
            binding.appliedValue = synchronizedValue;
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            disbandConfirmation.clear();
            resetChord(true);
        }
    }

    static String synchronizedValue(String settingValue, String appliedValue, String mappingValue) {
        String storedMappingValue = storedValue(mappingValue);
        if (appliedValue == null) {
            return KeybindSetting.UNBOUND.equals(settingValue)
                    && !KeybindSetting.UNBOUND.equals(storedMappingValue)
                    ? storedMappingValue : settingValue;
        }
        return !settingValue.equals(appliedValue) ? settingValue : storedMappingValue;
    }

    private static String storedValue(String mappingValue) {
        return InputConstants.UNKNOWN.getName().equals(mappingValue)
                ? KeybindSetting.UNBOUND : mappingValue;
    }

    private static InputConstants.Key key(String storedValue) {
        if (KeybindSetting.UNBOUND.equals(storedValue)) return InputConstants.UNKNOWN;
        try {
            return InputConstants.getKey(storedValue);
        } catch (IllegalArgumentException ignored) {
            return InputConstants.UNKNOWN;
        }
    }

    static int topRowDigit(int key) {
        return key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6
                ? key - GLFW.GLFW_KEY_0 : 0;
    }

    static String pingCooldownMessage(int seconds) {
        return "Ping on cooldown (" + seconds + "s remaining)";
    }

    private enum Action { OPEN, JOIN, CLOSE, LEAVE_DISBAND, PARTY_FILLED, PING, LOCK, CREATE, KICK }
    private enum ChordMode { NONE, CREATE, KICK }

    private record Selection(int digit, LfgProtocol.RaidType raid, UUID targetId,
                             String targetIgn, UUID lobbyId) {}

    private static final class Binding {
        private final KeybindSetting setting;
        private final KeyMapping mapping;
        private String appliedValue;

        private Binding(KeybindSetting setting, KeyMapping mapping) {
            this.setting = setting;
            this.mapping = mapping;
        }
    }
}
