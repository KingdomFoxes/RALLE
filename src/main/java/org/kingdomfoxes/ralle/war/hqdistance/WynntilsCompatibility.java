package org.kingdomfoxes.ralle.war.hqdistance;

import net.fabricmc.loader.api.FabricLoader;

public final class WynntilsCompatibility {
    public static final String SUPPORTED_VERSION = "4.2.7";

    private WynntilsCompatibility() {}

    public static Status detect() {
        return FabricLoader.getInstance().getModContainer("wynntils")
                .map(container -> status(true, container.getMetadata().getVersion().getFriendlyString()))
                .orElse(Status.MISSING);
    }

    static Status status(boolean installed, String version) {
        if (!installed) return Status.MISSING;
        String normalizedVersion = version != null && version.startsWith("v") ? version.substring(1) : version;
        return SUPPORTED_VERSION.equals(normalizedVersion) ? Status.SUPPORTED : Status.UNSUPPORTED;
    }

    public enum Status {
        SUPPORTED,
        MISSING,
        UNSUPPORTED;

        public boolean supported() {
            return this == SUPPORTED;
        }
    }
}
