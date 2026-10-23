package org.kingdomfoxes.ralle.classes;

/** One selector per physical hold. Cancellation/submission never reopens it until release. */
public final class ClassModifierState {
    private boolean active;
    private boolean blocked;
    public boolean active() { return active; }
    public boolean observe(boolean held, boolean allowed) {
        if (!held) { active = false; blocked = false; return false; }
        if (!allowed) { close(true); return false; }
        if (blocked || active) return false;
        active = true;
        return true;
    }
    public void close(boolean held) { active = false; blocked = held; }
}
