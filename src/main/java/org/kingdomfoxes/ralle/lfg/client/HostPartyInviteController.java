package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.network.chat.ClickEvent;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Detects authoritative host lobby fills and owns the single bounded, paced invitation queue.
 */
public final class HostPartyInviteController {
    public static final int MAX_PENDING = 3;
    public static final long COMMAND_INTERVAL_MILLIS = 600;

    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final String STALE = "That party invitation is no longer available.";
    private static final String LOST_AUTHORITY = "You are no longer the lobby host.";
    private static final String NO_TARGETS = "No valid party invite targets remain.";

    private final RaidLfgService service;
    private final PartyCommandExecutor commands;
    private final HostPartyInviteSink sink;
    private final Predicate<UUID> persistentCardExists;
    private final LongSupplier clockMillis;
    private final ArrayDeque<PendingInvite> queue = new ArrayDeque<>();
    private final Set<TargetKey> pendingTargets = new HashSet<>();
    private final Set<UUID> cardOffers = new HashSet<>();
    private long nextCommandAtMillis;

    public HostPartyInviteController(RaidLfgService service, PartyCommandExecutor commands,
                                     HostPartyInviteSink sink, Predicate<UUID> persistentCardExists) {
        this(service, commands, sink, persistentCardExists, System::currentTimeMillis);
    }

    HostPartyInviteController(RaidLfgService service, PartyCommandExecutor commands,
                              HostPartyInviteSink sink, Predicate<UUID> persistentCardExists,
                              LongSupplier clockMillis) {
        this.service = service;
        this.commands = commands;
        this.sink = sink;
        this.persistentCardExists = persistentCardExists;
        this.clockMillis = clockMillis;
        service.store().observeLobbyChanges(this::onLobbyChange);
    }

    private synchronized void onLobbyChange(RaidLfgStore.LobbyChange change) {
        var current = change.current();
        if (!viewerHosts(current)) {
            cancelLobby(change.lobbyId());
            return;
        }

        discardInvalidPending();
        if (!full(current)) cardOffers.remove(change.lobbyId());

        var previous = change.previous();
        boolean genuineFill = change.origin() == RaidLfgStore.UpdateOrigin.LIVE
                && previous != null
                && previous.revision() < current.revision()
                && previous.members().size() < previous.capacity()
                && full(current);
        if (!genuineFill) return;

        var targets = validTargets(current);
        if (targets.isEmpty()) return;
        if (persistentCardExists.test(current.lobbyId())) {
            cardOffers.add(current.lobbyId());
        } else {
            sink.partyFilled(current.lobbyId(), targets);
        }
    }

    public synchronized void tick() {
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) {
            cancelAll();
            return;
        }
        discardInvalidPending();
        long now = clockMillis.getAsLong();
        if (queue.isEmpty() || now < nextCommandAtMillis) return;

        var invite = queue.removeFirst();
        pendingTargets.remove(invite.key());
        var member = currentTarget(invite.lobbyId(), invite.memberId());
        if (member != null) {
            commands.invite(member.ign());
            nextCommandAtMillis = now + COMMAND_INTERVAL_MILLIS;
        }
        if (queue.isEmpty()) nextCommandAtMillis = 0;
    }

    public synchronized boolean handleClick(ClickEvent event) {
        var parsed = HostPartyInviteClickActions.parse(event);
        if (parsed.isEmpty()) return false;
        var action = parsed.get();
        if (action.invitesAll()) inviteAll(action.lobbyId());
        else inviteMember(action.lobbyId(), action.memberId());
        return true;
    }

    public synchronized boolean inviteAll(UUID lobbyId) {
        var lobby = currentHostedLobby(lobbyId);
        if (lobby == null) return rejectForCurrentState(lobbyId);
        discardInvalidPending();
        if (!queue.isEmpty()) {
            sink.error(NO_TARGETS);
            return false;
        }

        int accepted = 0;
        for (var member : validTargets(lobby)) {
            if (queue.size() >= MAX_PENDING) break;
            if (enqueue(lobbyId, member)) accepted++;
        }
        if (accepted == 0) {
            sink.error(NO_TARGETS);
            return false;
        }
        nextCommandAtMillis = clockMillis.getAsLong();
        cardOffers.remove(lobbyId);
        return true;
    }

    public synchronized boolean inviteMember(UUID lobbyId, UUID memberId) {
        var lobby = currentHostedLobby(lobbyId);
        if (lobby == null) return rejectForCurrentState(lobbyId);
        discardInvalidPending();
        var member = target(lobby, memberId);
        if (member == null) {
            sink.error(STALE);
            return false;
        }
        if (queue.size() >= MAX_PENDING || !enqueue(lobbyId, member)) {
            sink.error(NO_TARGETS);
            return false;
        }
        if (queue.size() == 1) nextCommandAtMillis = clockMillis.getAsLong();
        return true;
    }

    public synchronized boolean hasCardOffer(UUID lobbyId) {
        var lobby = service.store().state().lobbies().get(lobbyId);
        if (!cardOffers.contains(lobbyId) || !viewerHosts(lobby) || !full(lobby)) {
            cardOffers.remove(lobbyId);
            return false;
        }
        return true;
    }

    synchronized int pendingCount() {
        return queue.size();
    }

    private boolean enqueue(UUID lobbyId, LfgProtocol.Member member) {
        if (!IGN.matcher(member.ign()).matches()) return false;
        var invite = new PendingInvite(lobbyId, member.minecraftUuid());
        if (!pendingTargets.add(invite.key())) return false;
        queue.addLast(invite);
        return true;
    }

    private boolean rejectForCurrentState(UUID lobbyId) {
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE
                || !service.store().state().lobbies().containsKey(lobbyId)) {
            sink.error(STALE);
        } else {
            sink.error(LOST_AUTHORITY);
        }
        return false;
    }

    private LfgProtocol.Lobby currentHostedLobby(UUID lobbyId) {
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) return null;
        var lobby = service.store().state().lobbies().get(lobbyId);
        return viewerHosts(lobby) ? lobby : null;
    }

    private boolean viewerHosts(LfgProtocol.Lobby lobby) {
        var viewer = service.store().state().viewer();
        return lobby != null && viewer != null && lobby.hostedBy(viewer.minecraftUuid());
    }

    private LfgProtocol.Member currentTarget(UUID lobbyId, UUID memberId) {
        var lobby = currentHostedLobby(lobbyId);
        return lobby == null ? null : target(lobby, memberId);
    }

    private static LfgProtocol.Member target(LfgProtocol.Lobby lobby, UUID memberId) {
        return lobby.members().stream()
                .filter(member -> member.minecraftUuid().equals(memberId))
                .filter(member -> !member.minecraftUuid().equals(lobby.hostMinecraftUuid()))
                .filter(member -> IGN.matcher(member.ign()).matches())
                .findFirst()
                .orElse(null);
    }

    private static List<LfgProtocol.Member> validTargets(LfgProtocol.Lobby lobby) {
        return lobby.members().stream()
                .filter(member -> !member.minecraftUuid().equals(lobby.hostMinecraftUuid()))
                .filter(member -> IGN.matcher(member.ign()).matches())
                .limit(MAX_PENDING)
                .toList();
    }

    private static boolean full(LfgProtocol.Lobby lobby) {
        return lobby.members().size() >= lobby.capacity();
    }

    private void discardInvalidPending() {
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var invite = iterator.next();
            if (currentTarget(invite.lobbyId(), invite.memberId()) != null) continue;
            iterator.remove();
            pendingTargets.remove(invite.key());
        }
        if (queue.isEmpty()) nextCommandAtMillis = 0;
    }

    private void cancelLobby(UUID lobbyId) {
        cardOffers.remove(lobbyId);
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var invite = iterator.next();
            if (!invite.lobbyId().equals(lobbyId)) continue;
            iterator.remove();
            pendingTargets.remove(invite.key());
        }
        if (queue.isEmpty()) nextCommandAtMillis = 0;
    }

    private void cancelAll() {
        queue.clear();
        pendingTargets.clear();
        cardOffers.clear();
        nextCommandAtMillis = 0;
    }

    private record PendingInvite(UUID lobbyId, UUID memberId) {
        TargetKey key() {
            return new TargetKey(lobbyId, memberId);
        }
    }

    private record TargetKey(UUID lobbyId, UUID memberId) {}
}
