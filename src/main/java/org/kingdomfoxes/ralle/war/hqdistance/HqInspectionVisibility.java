package org.kingdomfoxes.ralle.war.hqdistance;

public final class HqInspectionVisibility {
    private HqInspectionVisibility() {}

    public static boolean visible(
            boolean enabled,
            boolean supported,
            boolean controlHeld,
            boolean territoryHovered,
            boolean activeAttackTimer
    ) {
        return enabled && supported && controlHeld && territoryHovered && !activeAttackTimer;
    }
}
