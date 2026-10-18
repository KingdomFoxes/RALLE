package org.kingdomfoxes.ralle.lfg.client;

import java.util.function.Supplier;

/** Modifier-plus-digit selection: the latest fresh press wins and its release submits. */
public final class LfgChordState<T> {
    private T selection;
    private int digit;
    private boolean completed;

    public void begin() {
        selection = null;
        digit = 0;
        completed = false;
    }

    public Press<T> press(int pressedDigit, Supplier<T> selectionFactory) {
        if (completed) return new Press<>(selection, false);
        if (selection == null || digit != pressedDigit) {
            digit = pressedDigit;
            selection = selectionFactory.get();
        }
        return new Press<>(selection, true);
    }

    public Release<T> release(int releasedDigit, boolean modifierHeld) {
        if (completed || selection == null || digit != releasedDigit) {
            return new Release<>(false, null);
        }
        T execution = modifierHeld ? selection : null;
        selection = null;
        digit = 0;
        completed = true;
        return new Release<>(true, execution);
    }

    public boolean completed() {
        return completed;
    }

    /** Another input source won submission. A later digit release must never dispatch a second action. */
    public void finish() {
        selection = null;
        digit = 0;
        completed = true;
    }

    public void reset() {
        begin();
    }

    public record Press<T>(T selection, boolean accepted) {}
    public record Release<T>(boolean matched, T execution) {}
}
