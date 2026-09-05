package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe immutable client projection consumed by the Raid LFG presentation. */
public final class RaidLfgStore {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<LobbyChangeSink> changeSinks = new CopyOnWriteArrayList<>();
    private volatile State state = State.empty();

    public State state() {
        return state;
    }

    public AutoCloseable observe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public AutoCloseable observeLobbyChanges(LobbyChangeSink sink) {
        changeSinks.add(sink);
        return () -> changeSinks.remove(sink);
    }

    public synchronized void replace(LfgProtocol.Snapshot snapshot) {
        replace(snapshot, UpdateOrigin.SNAPSHOT);
    }

    public synchronized void replace(LfgProtocol.Snapshot snapshot, UpdateOrigin origin) {
        requireVersion(snapshot.protocolVersion());
        var previous = state.lobbies();
        var lobbies = new LinkedHashMap<UUID, LfgProtocol.Lobby>();
        for (var lobby : snapshot.lobbies()) lobbies.put(lobby.lobbyId(), lobby);
        state = new State(snapshot.revision(), snapshot.viewer(), snapshot.capabilities(), lobbies);
        emitDiff(previous, lobbies, origin);
        notifyListeners();
    }

    public synchronized boolean apply(LfgProtocol.Mutation mutation) {
        requireVersion(mutation.protocolVersion());
        return upsert(mutation.revision(), mutation.lobby(), UpdateOrigin.LOCAL_MUTATION);
    }

    public synchronized boolean upsert(long globalRevision, LfgProtocol.Lobby lobby) {
        return upsert(globalRevision, lobby, UpdateOrigin.LIVE);
    }

    public synchronized boolean upsert(long globalRevision, LfgProtocol.Lobby lobby, UpdateOrigin origin) {
        if (globalRevision <= state.revision()) return false;
        var updated = new LinkedHashMap<>(state.lobbies());
        var previous = updated.put(lobby.lobbyId(), lobby);
        state = new State(globalRevision, state.viewer(), refreshedCapabilities(updated), updated);
        emitChange(previous, lobby, origin);
        notifyListeners();
        return true;
    }

    public synchronized boolean remove(long globalRevision, UUID lobbyId) {
        return remove(globalRevision, lobbyId, UpdateOrigin.LIVE);
    }

    public synchronized boolean remove(long globalRevision, UUID lobbyId, UpdateOrigin origin) {
        return remove(globalRevision, lobbyId, origin, null);
    }

    public synchronized boolean remove(long globalRevision, UUID lobbyId, UpdateOrigin origin, String reason) {
        if (globalRevision <= state.revision()) return false;
        var updated = new LinkedHashMap<>(state.lobbies());
        var previous = updated.remove(lobbyId);
        state = new State(globalRevision, state.viewer(), refreshedCapabilities(updated), updated);
        if (previous != null) {
            var change = new LobbyChange(previous, null, origin, reason);
            for (var sink : changeSinks) sink.changed(change);
        }
        notifyListeners();
        return true;
    }

    /** Keeps the authorized RAID_ALREADY_LISTED payload visible without inventing a global revision. */
    public synchronized void remember(LfgProtocol.Lobby lobby) {
        var existing = state.lobbies().get(lobby.lobbyId());
        if (existing != null && existing.revision() >= lobby.revision()) return;
        var updated = new LinkedHashMap<>(state.lobbies());
        updated.put(lobby.lobbyId(), lobby);
        state = new State(state.revision(), state.viewer(), state.capabilities(), updated);
        emitChange(existing, lobby, UpdateOrigin.LOCAL_MUTATION);
        notifyListeners();
    }

    public synchronized void clear() {
        var previous = state.lobbies();
        state = State.empty();
        for (var lobby : previous.values()) emitChange(lobby, null, UpdateOrigin.CLEAR);
        notifyListeners();
    }

    private void emitDiff(Map<UUID, LfgProtocol.Lobby> previous,
                          Map<UUID, LfgProtocol.Lobby> current,
                          UpdateOrigin origin) {
        var ids = new java.util.LinkedHashSet<UUID>();
        ids.addAll(previous.keySet());
        ids.addAll(current.keySet());
        for (var id : ids) {
            var before = previous.get(id);
            var after = current.get(id);
            if (!Objects.equals(before, after)) emitChange(before, after, origin);
        }
    }

    private void emitChange(LfgProtocol.Lobby previous, LfgProtocol.Lobby current, UpdateOrigin origin) {
        var change = new LobbyChange(previous, current, origin);
        for (var sink : changeSinks) sink.changed(change);
    }

    private void notifyListeners() {
        for (var listener : listeners) listener.run();
    }

    private LfgProtocol.ViewerCapabilities refreshedCapabilities(Map<UUID, LfgProtocol.Lobby> lobbies) {
        if (state.viewer() == null || state.capabilities() == null) return state.capabilities();
        boolean active = lobbies.values().stream().anyMatch(lobby -> lobby.contains(state.viewer().minecraftUuid()));
        return new LfgProtocol.ViewerCapabilities(!active, state.capabilities().browse(),
                active ? Map.of("create", "PLAYER_ALREADY_ACTIVE") : Map.of());
    }

    private static void requireVersion(int version) {
        if (version != LfgProtocol.VERSION) {
            throw new LfgProtocolException("Expected protocol " + LfgProtocol.VERSION + " but received " + version);
        }
    }

    public enum UpdateOrigin {
        LIVE,
        SNAPSHOT,
        LOCAL_MUTATION,
        CLEAR
    }

    @FunctionalInterface
    public interface LobbyChangeSink {
        void changed(LobbyChange change);
    }

    public record LobbyChange(LfgProtocol.Lobby previous, LfgProtocol.Lobby current,
                              UpdateOrigin origin, String removalReason) {
        public LobbyChange(LfgProtocol.Lobby previous, LfgProtocol.Lobby current, UpdateOrigin origin) {
            this(previous, current, origin, null);
        }
        public LobbyChange {
            origin = Objects.requireNonNull(origin, "origin");
            if (previous == null && current == null) {
                throw new IllegalArgumentException("A lobby change must carry previous or current state");
            }
        }

        public UUID lobbyId() {
            return current != null ? current.lobbyId() : previous.lobbyId();
        }
    }

    public record State(long revision, LfgProtocol.PlayerIdentity viewer,
                        LfgProtocol.ViewerCapabilities capabilities,
                        Map<UUID, LfgProtocol.Lobby> lobbies) {
        public State {
            lobbies = Collections.unmodifiableMap(new LinkedHashMap<>(lobbies));
        }

        static State empty() {
            return new State(-1, null, null, Map.of());
        }

        public List<LfgProtocol.Lobby> lobbyList() {
            return List.copyOf(lobbies.values());
        }
    }
}
