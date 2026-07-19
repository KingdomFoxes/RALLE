package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe immutable client projection consumed by the Raid LFG presentation. */
public final class RaidLfgStore {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile State state = State.empty();

    public State state() {
        return state;
    }

    public AutoCloseable observe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public synchronized void replace(LfgProtocol.Snapshot snapshot) {
        requireVersion(snapshot.protocolVersion());
        var lobbies = new LinkedHashMap<UUID, LfgProtocol.Lobby>();
        for (var lobby : snapshot.lobbies()) lobbies.put(lobby.lobbyId(), lobby);
        state = new State(snapshot.revision(), snapshot.viewer(), snapshot.capabilities(), lobbies);
        notifyListeners();
    }

    public synchronized boolean apply(LfgProtocol.Mutation mutation) {
        requireVersion(mutation.protocolVersion());
        return upsert(mutation.revision(), mutation.lobby());
    }

    public synchronized boolean upsert(long globalRevision, LfgProtocol.Lobby lobby) {
        if (globalRevision <= state.revision()) return false;
        var updated = new LinkedHashMap<>(state.lobbies());
        updated.put(lobby.lobbyId(), lobby);
        state = new State(globalRevision, state.viewer(), refreshedCapabilities(updated), updated);
        notifyListeners();
        return true;
    }

    public synchronized boolean remove(long globalRevision, UUID lobbyId) {
        if (globalRevision <= state.revision()) return false;
        var updated = new LinkedHashMap<>(state.lobbies());
        updated.remove(lobbyId);
        state = new State(globalRevision, state.viewer(), refreshedCapabilities(updated), updated);
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
        notifyListeners();
    }

    public synchronized void clear() {
        state = State.empty();
        notifyListeners();
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
