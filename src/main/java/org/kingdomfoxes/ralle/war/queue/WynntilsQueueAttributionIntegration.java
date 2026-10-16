package org.kingdomfoxes.ralle.war.queue;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Managers;
import com.wynntils.core.consumers.overlays.OverlaySize;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.chat.event.ChatMessageEvent;
import com.wynntils.handlers.chat.type.RecipientType;
import com.wynntils.models.territories.TerritoryAttackTimer;
import com.wynntils.overlays.TerritoryAttackTimerOverlay;
import com.wynntils.utils.render.TextRenderTask;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import org.kingdomfoxes.ralle.chat.identity.GuildChatIdentityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** All direct Wynntils event, model, and render-task references are isolated here. */
final class WynntilsQueueAttributionIntegration implements QueueAttributionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WynntilsQueueAttributionIntegration.class);
    // Reserve 120 extra logical pixels for a full 16-character IGN and the arrow prefix.
    private static final float ATTRIBUTED_TIMER_WIDTH = 370f;
    private static final Pattern CAPTURE_MESSAGE = Pattern.compile(
            "^\\s*[^\\[]*\\[[^\\]\\r\\n]{1,64}] (?:has )?captured the territory (.{1,128})\\.$"
    );

    private final QueueAnnouncementParser parser;
    private final QueueAttributionTracker tracker;
    private boolean registered;
    private final QueueAttributionSession session;
    private boolean usable;
    private boolean timerSizeChecked;

    WynntilsQueueAttributionIntegration(QueueAnnouncementParser parser, QueueAttributionTracker tracker) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.session = new QueueAttributionSession(tracker);
    }

    @Override public boolean ready() {
        return WynntilsMod.getModLoader() != null && Models.WorldState.onWorld();
    }

    @Override public void register() {
        if (registered) return;
        WynntilsMod.registerEventListener(this);
        registered = true;
    }

    @Override public void unregister() {
        if (!registered) return;
        registered = false;
        WynntilsMod.unregisterEventListener(this);
    }

    @Override public boolean registered() {
        return registered;
    }

    @Override public void tick(Minecraft minecraft) {
        widenDefaultTimerBox();
        usable = session.update(minecraft.getUser().getProfileId().toString(), Models.Guild.getGuildName());
        if (!usable) return;

        List<AttackTimerSnapshot> timers = Models.GuildAttackTimer.getAttackTimers().stream()
                .map(timer -> new AttackTimerSnapshot(timer.territoryName(), timer.timerEnd()))
                .toList();
        tracker.reconcile(timers);
    }

    /** Runs for saved enabled settings too; never forces a width during rendering or editing. */
    private void widenDefaultTimerBox() {
        if (timerSizeChecked) return;
        try {
            if (Managers.Config.isLoadingConfigOptions()) return;
            var overlay = Managers.Overlay.getOverlay(TerritoryAttackTimerOverlay.class);
            if (overlay == null) return;
            var size = overlay.getConfigOptionFromString("size");
            if (size.isEmpty()) return;
            timerSizeChecked = true;
            widenUntouchedSize(size.get());
        } catch (RuntimeException | LinkageError exception) {
            timerSizeChecked = true;
            // Optional sizing must not disable attribution if a later Wynntils changes its config API.
            LOGGER.warn("Could not widen the default Wynntils Guild Attack Timers box", exception);
        }
    }

    @SuppressWarnings("unchecked") // The runtime value is checked before narrowing the config type.
    private static void widenUntouchedSize(Config<?> config) {
        if (config.valueChanged() || !(config.get() instanceof OverlaySize current)
                || current.getWidth() != 250f || current.getHeight() != 110f) return;
        var expanded = current.copy();
        expanded.setWidth(ATTRIBUTED_TIMER_WIDTH);
        // setValue refreshes Wynntils' cached wrapping width and marks this as an edited size.
        // Copying avoids mutating its default object; touched persists the change across restarts.
        ((Config<OverlaySize>) config).setValue(expanded);
        config.touched();
    }

    /** Territory names are needed only when resolving an incoming announcement, never every tick. */
    private static Set<String> territoryNames() {
        var canonicalNames = new LinkedHashSet<String>();
        Models.Territory.getTerritoryNames()
                .map(Models.Territory::getTerritoryProfile)
                .filter(Objects::nonNull)
                .map(profile -> profile.getFriendlyName())
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .forEach(canonicalNames::add);
        Models.GuildAttackTimer.getAttackTimers().stream()
                .map(TerritoryAttackTimer::territoryName).forEach(canonicalNames::add);
        return canonicalNames;
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onChatMessage(ChatMessageEvent.Match event) {
        try {
            if (!registered || !usable || !ready()) return;
            if (event.getRecipientType() == RecipientType.GUILD || event.getRecipientType() == RecipientType.INFO) {
                GuildChatIdentityResolver.resolve(event.getMessage().getComponent())
                        .flatMap(message -> parser.parse(message, territoryNames()))
                        .ifPresent(tracker::observe);
            }
            if (event.getRecipientType() == RecipientType.INFO) observeCapture(event.getMessage().getComponent());
        } catch (RuntimeException | LinkageError exception) {
            QueueAttributionService.fail(exception);
        }
    }

    @Override public void decorateTimer(Object timerObject, Object taskObject) {
        if (!(timerObject instanceof TerritoryAttackTimer timer) || !(taskObject instanceof TextRenderTask task)) return;
        Component decorated = QueueAttributionFormatter.format(
                task.getText().getComponent(),
                usable ? tracker.attributionFor(timer.territoryName()) : java.util.Optional.empty(),
                Minecraft.getInstance().getUser().getName(),
                Component.translatable("ralle.war.queue.unknown"), QueueAttributionService.selfColor(),
                System.currentTimeMillis(), QueueAttributionService::otherColor);
        task.setText(StyledText.fromComponent(decorated));
    }

    @Override public void decoratePreview(Object taskObject) {
        if (!(taskObject instanceof TextRenderTask task)) return;
        String self = Minecraft.getInstance().getUser().getName();
        Component decorated = QueueAttributionFormatter.format(
                task.getText().getComponent(), java.util.Optional.of(self), self,
                Component.translatable("ralle.war.queue.unknown"), QueueAttributionService.selfColor());
        task.setText(StyledText.fromComponent(decorated));
    }

    @Override public void clear() {
        session.clear();
        usable = false;
    }

    private void observeCapture(Component component) {
        String text = component.getString();
        if (text.length() > 512 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return;
        var match = CAPTURE_MESSAGE.matcher(text);
        if (!match.matches()) return;
        String canonical = QueueAnnouncementParser.canonicalTerritory(match.group(1), territoryNames());
        if (canonical != null) tracker.captured(canonical);
    }

}
