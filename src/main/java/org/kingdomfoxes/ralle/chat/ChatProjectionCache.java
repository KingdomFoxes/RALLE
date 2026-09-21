package org.kingdomfoxes.ralle.chat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

/** Identity-keyed wrapping for the newest retained lines; no off-screen history is retained. */
public final class ChatProjectionCache<M, L> {
    private IdentityHashMap<M, List<L>> wrapped = new IdentityHashMap<>();
    private Object layout;

    public List<L> project(List<M> newestFirst, int limit, Object layout,
                           Function<M, List<L>> wrapInDisplayOrder) {
        if (!java.util.Objects.equals(this.layout, layout)) clear();
        this.layout = layout;
        var retained = new IdentityHashMap<M, List<L>>();
        var result = new ArrayList<L>();
        for (var message : newestFirst) {
            if (result.size() >= limit) break;
            var lines = wrapped.get(message);
            if (lines == null) {
                var display = wrapInDisplayOrder.apply(message);
                // Match vanilla's newest-first storage, including partial oldest messages.
                lines = List.copyOf(display.subList(Math.max(0, display.size() - limit), display.size()).reversed());
            }
            retained.put(message, lines);
            result.addAll(lines.subList(0, Math.min(lines.size(), limit - result.size())));
        }
        wrapped = retained;
        return result;
    }

    public void clear() { wrapped.clear(); layout = null; }
}
