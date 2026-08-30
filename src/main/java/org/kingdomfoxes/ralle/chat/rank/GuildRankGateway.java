package org.kingdomfoxes.ralle.chat.rank;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Fetches the public Fox-title directory used only for local chat presentation. */
@FunctionalInterface
public interface GuildRankGateway {
    CompletableFuture<Map<String, String>> fetchTitles();
}
