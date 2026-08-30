package org.kingdomfoxes.ralle.requeue;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.Arrays;
import java.util.Optional;

/** Wynncraft raid names accepted from chat and inventory item titles. */
public enum WynnRaid {
    NOTG("nest-of-the-grootslangs", "Nest of the Grootslangs", LfgProtocol.RaidType.NOTG),
    NOL("orphions-nexus-of-light", "Orphion's Nexus of Light", LfgProtocol.RaidType.NOL),
    TCC("the-canyon-colossus", "The Canyon Colossus", LfgProtocol.RaidType.TCC),
    TNA("the-nameless-anomaly", "The Nameless Anomaly", LfgProtocol.RaidType.TNA),
    TWP("the-warront-palace", "The Warront Palace", LfgProtocol.RaidType.TWP);

    private final String persistedId;
    private final String displayName;
    private final LfgProtocol.RaidType lfgType;

    WynnRaid(String persistedId, String displayName, LfgProtocol.RaidType lfgType) {
        this.persistedId = persistedId;
        this.displayName = displayName;
        this.lfgType = lfgType;
    }

    public String persistedId() {
        return persistedId;
    }

    public String displayName() {
        return displayName;
    }

    public LfgProtocol.RaidType lfgType() {
        return lfgType;
    }

    public static Optional<WynnRaid> fromDisplayName(String value) {
        String normalized = normalize(value);
        return Arrays.stream(values())
                .filter(raid -> normalize(raid.displayName).equals(normalized))
                .findFirst();
    }

    public static Optional<WynnRaid> fromPersistedId(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(raid -> raid.persistedId.equalsIgnoreCase(value.strip()))
                .findFirst();
    }

    static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
