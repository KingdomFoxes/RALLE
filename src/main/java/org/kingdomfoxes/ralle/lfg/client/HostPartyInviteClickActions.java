package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/** Opaque local chat actions. They are consumed before vanilla can send a command. */
public final class HostPartyInviteClickActions {
    private static final String ALL_PREFIX = "invite/all/";
    private static final String MEMBER_PREFIX = "invite/member/";

    private HostPartyInviteClickActions() {}

    public static ClickEvent inviteAll(UUID lobbyId) {
        return custom(ALL_PREFIX + lobbyId);
    }

    public static ClickEvent inviteMember(UUID lobbyId, UUID memberId) {
        return custom(MEMBER_PREFIX + lobbyId + "/" + memberId);
    }

    public static Optional<Action> parse(ClickEvent event) {
        if (!(event instanceof ClickEvent.Custom custom) || !"ralle".equals(custom.id().getNamespace())) {
            return Optional.empty();
        }
        String path = custom.id().getPath();
        try {
            if (path.startsWith(ALL_PREFIX)) {
                return Optional.of(new Action(UUID.fromString(path.substring(ALL_PREFIX.length())), null));
            }
            if (path.startsWith(MEMBER_PREFIX)) {
                String[] parts = path.substring(MEMBER_PREFIX.length()).split("/", -1);
                if (parts.length == 2) {
                    return Optional.of(new Action(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Treat malformed or hostile custom click metadata as an unrelated action.
        }
        return Optional.empty();
    }

    private static ClickEvent custom(String path) {
        return new ClickEvent.Custom(Identifier.fromNamespaceAndPath("ralle", path), Optional.empty());
    }

    public record Action(UUID lobbyId, UUID memberId) {
        public boolean invitesAll() {
            return memberId == null;
        }
    }
}
