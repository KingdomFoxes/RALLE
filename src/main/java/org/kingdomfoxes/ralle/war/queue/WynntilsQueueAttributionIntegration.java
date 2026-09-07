package org.kingdomfoxes.ralle.war.queue;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.chat.event.ChatMessageEvent;
import com.wynntils.handlers.chat.type.RecipientType;
import com.wynntils.models.territories.TerritoryAttackTimer;
import com.wynntils.models.worlds.event.WorldStateEvent;
import com.wynntils.models.worlds.type.WorldState;
import com.wynntils.utils.render.TextRenderTask;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import org.kingdomfoxes.ralle.chat.identity.GuildChatIdentityResolver;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** All direct Wynntils event, model, and render-task references are isolated here. */
final class WynntilsQueueAttributionIntegration implements QueueAttributionAdapter {
    private static final Pattern CAPTURE_MESSAGE = Pattern.compile(
            "^\\s*[^\\[]*\\[[^\\]\\r\\n]{1,64}] (?:has )?captured the territory (.{1,128})\\.$"
    );

    private final QueueAnnouncementParser parser;
    private final QueueAttributionTracker tracker;
    private boolean registered;
    private SessionIdentity sessionIdentity;

    WynntilsQueueAttributionIntegration(QueueAnnouncementParser parser, QueueAttributionTracker tracker) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
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
        SessionIdentity current = currentIdentity(minecraft);
        if (!current.equals(sessionIdentity)) {
            tracker.clear();
            sessionIdentity = current;
        }
        if (!current.usable()) {
            tracker.clear();
            return;
        }

        List<AttackTimerSnapshot> timers = Models.GuildAttackTimer.getAttackTimers().stream()
                .map(timer -> new AttackTimerSnapshot(timer.territoryName(), timer.timerEnd()))
                .toList();
        tracker.reconcile(timers);
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
            if (!registered || sessionIdentity == null || !sessionIdentity.usable()) return;
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

    @SubscribeEvent
    public void onWorldStateChanged(WorldStateEvent event) {
        try {
            if (event.getNewState() != WorldState.WORLD
                    || event.getOldState() != event.getNewState()
                    || event.isFirstJoinWorld()) {
                clear();
            }
        } catch (RuntimeException | LinkageError exception) {
            QueueAttributionService.fail(exception);
        }
    }

    @Override public void decorateTimer(Object timerObject, Object taskObject) {
        if (!(timerObject instanceof TerritoryAttackTimer timer) || !(taskObject instanceof TextRenderTask task)) return;
        Component decorated = QueueAttributionFormatter.format(
                task.getText().getComponent(),
                tracker.attributionFor(timer.territoryName()),
                Minecraft.getInstance().getUser().getName(),
                Component.translatable("ralle.war.queue.unknown"), QueueAttributionService.selfColor());
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
        tracker.clear();
        sessionIdentity = null;
    }

    private void observeCapture(Component component) {
        String text = component.getString();
        if (text.length() > 512 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return;
        var match = CAPTURE_MESSAGE.matcher(text);
        if (!match.matches()) return;
        String canonical = QueueAnnouncementParser.canonicalTerritory(match.group(1), territoryNames());
        if (canonical != null) tracker.captured(canonical);
    }

    private static SessionIdentity currentIdentity(Minecraft minecraft) {
        String guild = Objects.requireNonNullElse(Models.Guild.getGuildName(), "").strip();
        String character = Models.Character.hasCharacter()
                ? Objects.requireNonNullElse(Models.Character.getId(), "") : "";
        return new SessionIdentity(
                minecraft.getUser().getProfileId().toString(),
                minecraft.getUser().getName(),
                Models.WorldState.getCurrentWorldName(),
                guild,
                character);
    }

    private record SessionIdentity(String accountId, String accountName, String world, String guild, String character) {
        SessionIdentity {
            accountId = Objects.requireNonNullElse(accountId, "");
            accountName = Objects.requireNonNullElse(accountName, "");
            world = Objects.requireNonNullElse(world, "");
            guild = Objects.requireNonNullElse(guild, "");
            character = Objects.requireNonNullElse(character, "");
        }

        boolean usable() {
            return !accountId.isBlank() && !accountName.isBlank() && !world.isBlank()
                    && !guild.isBlank() && !character.isBlank();
        }
    }
}
