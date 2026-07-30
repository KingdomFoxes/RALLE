package org.kingdomfoxes.ralle.lfg.client;

import java.util.function.Supplier;

/** Small deterministic state machine for one modifier-plus-digit keyboard chord. */
final class LfgChordState<T> {
    private T selection;
    private int digit;
    private boolean ambiguous;
    private boolean completed;

    void begin() {
        selection = null;
        digit = 0;
        ambiguous = false;
        completed = false;
    }

    Press<T> press(int pressedDigit, Supplier<T> selectionFactory) {
        if (completed) return new Press<>(selection, ambiguous, false);
        if (selection != null && digit != pressedDigit) {
            ambiguous = true;
            return new Press<>(selection, true, true);
        }
        if (selection == null) {
            digit = pressedDigit;
            selection = selectionFactory.get();
        }
        return new Press<>(selection, ambiguous, true);
    }

    Release<T> release(int releasedDigit, boolean modifierHeld) {
        if (completed || selection == null || digit != releasedDigit) {
            return new Release<>(false, null);
        }
        T execution = !ambiguous && modifierHeld ? selection : null;
        selection = null;
        digit = 0;
        ambiguous = false;
        completed = true;
        return new Release<>(true, execution);
    }

    boolean completed() {
        return completed;
    }

    void reset() {
        begin();
    }

    record Press<T>(T selection, boolean ambiguous, boolean accepted) {}
    record Release<T>(boolean matched, T execution) {}
}
