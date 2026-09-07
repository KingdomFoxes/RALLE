package org.kingdomfoxes.ralle.war.hqdistance;

import net.fabricmc.loader.api.FabricLoader;

public final class WynntilsCompatibility {
    public static final String SUPPORTED_VERSION = "4.2.7";
    private static final int SUPPORTED_MAJOR = 4;
    private static final int SUPPORTED_MINOR = 2;
    private static final int SUPPORTED_PATCH = 7;

    private WynntilsCompatibility() {}

    public static Status detect() {
        return FabricLoader.getInstance().getModContainer("wynntils")
                .map(container -> status(true, container.getMetadata().getVersion().getFriendlyString()))
                .orElse(Status.MISSING);
    }

    static Status status(boolean installed, String version) {
        if (!installed) return Status.MISSING;
        return atLeastSupported(version) ? Status.SUPPORTED : Status.UNSUPPORTED;
    }

    /** Accept the pinned API baseline and newer Wynntils releases (including 4.2.10). */
    static boolean atLeastSupported(String version) {
        if (version == null) return false;
        String normalized = version.strip();
        if (normalized.startsWith("v")) normalized = normalized.substring(1);
        String[] parts = normalized.split("[+\\-]", 2);
        if (parts.length == 0) return false;
        try {
            String[] numbers = parts[0].split("\\.");
            if (numbers.length < 3) return false;
            int major = Integer.parseInt(numbers[0]);
            int minor = Integer.parseInt(numbers[1]);
            int patch = Integer.parseInt(numbers[2]);
            return major > SUPPORTED_MAJOR
                    || major == SUPPORTED_MAJOR && (minor > SUPPORTED_MINOR
                    || minor == SUPPORTED_MINOR && patch >= SUPPORTED_PATCH);
        } catch (NumberFormatException ignored) {
            return false;
        }
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
