package org.kingdomfoxes.ralle.war.hqdistance;

final class InspectionModifier {
    private InspectionModifier() {}

    static boolean held(boolean leftControl, boolean rightControl) {
        return leftControl || rightControl;
    }
}
