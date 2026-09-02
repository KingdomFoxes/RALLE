package org.kingdomfoxes.ralle.api.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Toolkit-neutral registry used by a settings presentation adapter. */
public final class CustomSettingsPanelRegistry<C, R> {
    private final Map<String, CustomSettingsPanelProvider<C, R>> providers = new LinkedHashMap<>();

    public void register(CustomSettingsPanelProvider<C, R> provider) {
        Objects.requireNonNull(provider, "provider");
        if (providers.putIfAbsent(provider.id(), provider) != null) {
            throw new IllegalArgumentException("Duplicate custom settings panel provider: " + provider.id());
        }
    }

    public R render(String providerId, C context) {
        var provider = providers.get(Objects.requireNonNull(providerId, "providerId"));
        if (provider == null) throw new IllegalArgumentException("Unknown custom settings panel provider: " + providerId);
        return provider.render(context);
    }

    public boolean contains(String providerId) {
        return providers.containsKey(providerId);
    }
}
