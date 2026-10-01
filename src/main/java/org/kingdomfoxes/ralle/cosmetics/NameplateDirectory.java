package org.kingdomfoxes.ralle.cosmetics;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Narrow public Fox lookup port. Authentication and self-style mutation are separate operations. */
public interface NameplateDirectory {
    CompletableFuture<CosmeticLookupJson.Lookup> lookup(List<UUID> uuids);
}
