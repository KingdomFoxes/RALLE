package org.kingdomfoxes.ralle.cosmetics;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One Fox lookup result. Unknown, revoked, or invalid metadata never produces a decoration. */
public record CosmeticIdentity(UUID minecraftUuid, Set<String> grants, String selectedStyleId, long revision) {
    public CosmeticIdentity {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        grants = Set.copyOf(Objects.requireNonNull(grants, "grants"));
        if (!Set.of("supporter", "contributor", "admin").containsAll(grants) || revision < 0)
            throw new IllegalArgumentException("Invalid Fox cosmetic metadata");
        if (selectedStyleId != null && !NameplateStyle.allowed(selectedStyleId, grants))
            throw new IllegalArgumentException("Unrecognized or unauthorized style");
    }

    public NameplateStyle selectedStyle() {
        return selectedStyleId == null ? null : NameplateStyle.byId(selectedStyleId).orElse(null);
    }

    /** Settings access follows Fox grants even before the player selects a style. */
    public boolean hasNameplateAccess() {
        return !NameplateStyle.available(grants).isEmpty();
    }

    public static CosmeticIdentity neutral(UUID uuid, long revision) {
        return new CosmeticIdentity(uuid, Set.of(), null, revision);
    }
}
