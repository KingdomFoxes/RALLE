package org.kingdomfoxes.ralle.war.consumables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compiled first-match matcher with a bounded displayed-name cache. */
public final class ConsumableHighlightMatcher {
    public static final int DEFAULT_CACHE_SIZE = 512;
    private final int cacheSize;
    private final Map<String, Optional<HighlightStyle>> cache;
    private List<CompiledRule> compiled = List.of();

    public ConsumableHighlightMatcher(List<ConsumableHighlightRule> rules) {
        this(rules, DEFAULT_CACHE_SIZE);
    }

    ConsumableHighlightMatcher(List<ConsumableHighlightRule> rules, int cacheSize) {
        if (cacheSize < 1) throw new IllegalArgumentException("cacheSize must be positive");
        this.cacheSize = cacheSize;
        this.cache = new LinkedHashMap<>(cacheSize, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<HighlightStyle>> eldest) {
                return size() > ConsumableHighlightMatcher.this.cacheSize;
            }
        };
        update(rules);
    }

    public synchronized void update(List<ConsumableHighlightRule> rules) {
        var next = new ArrayList<CompiledRule>();
        for (var rule : ConsumableHighlightValidation.validate(rules)) {
            var terms = new ArrayList<String>();
            terms.add(ConsumableNameNormalizer.normalize(rule.name()));
            rule.aliases().stream().map(ConsumableNameNormalizer::normalize).forEach(terms::add);
            next.add(new CompiledRule(List.copyOf(terms), rule.style()));
        }
        compiled = List.copyOf(next);
        cache.clear();
    }

    public synchronized Optional<HighlightStyle> match(String displayedName) {
        if (displayedName == null || compiled.isEmpty()) return Optional.empty();
        // Key by the unmodified name so repeated slot renders skip Unicode normalization too.
        var cached = cache.get(displayedName);
        if (cached != null) return cached;
        var normalized = ConsumableNameNormalizer.normalize(displayedName);
        var result = normalized.isEmpty() ? Optional.<HighlightStyle>empty() : find(normalized);
        cache.put(displayedName, result);
        return result;
    }

    public synchronized int cachedNames() {
        return cache.size();
    }

    private Optional<HighlightStyle> find(String normalizedName) {
        for (var rule : compiled) {
            for (var term : rule.terms()) {
                if (ConsumableNameNormalizer.containsPhrase(normalizedName, term)) return Optional.of(rule.style());
            }
        }
        return Optional.empty();
    }

    private record CompiledRule(List<String> terms, HighlightStyle style) {}
}
