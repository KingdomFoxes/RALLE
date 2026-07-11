package org.kingdomfoxes.ralle.api.feature;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FeatureRegistry {
    private final Map<String, RalleFeature> features = new LinkedHashMap<>();
    private boolean sealed;

    public void register(RalleFeature feature) {
        requireOpen();
        Objects.requireNonNull(feature, "feature");
        var id = IdentifierRules.requireValid(feature.id(), "feature id");
        if (features.putIfAbsent(id, feature) != null) {
            throw new IllegalArgumentException("Duplicate feature id: " + id);
        }
    }

    public Collection<RalleFeature> all() {
        return List.copyOf(features.values());
    }

    public void seal() {
        sealed = true;
    }

    private void requireOpen() {
        if (sealed) throw new IllegalStateException("Feature registration is sealed");
    }
}
