package org.kingdomfoxes.ralle.classes;

import java.util.List;
import java.util.UUID;

/** Single bounded operation; all stages advance from observations, never sleeps or retries. */
public final class ClassSwitchController {
    public static final long TIMEOUT_MILLIS = 15_000;
    public enum State { IDLE, CAPTURING, WAITING_STAGE, WAITING_MENU, WAITING_TRANSITION, WAITING_WORLD }
    public enum Failure { CANCELLED, TIMEOUT, DISCONNECTED, UNEXPECTED_MENU, AMBIGUOUS_TARGET, ACCOUNT_CHANGED, UNAVAILABLE }

    /** Native adapter owns rendering and validates the live book before passing resolved targets. */
    public interface Actions {
        void captureFrame();
        void sendClassCommand();
        void rightClickSelectionStage();
        void selectCharacter(int containerId, int slot);
        void releaseFrame();
        void failed(Failure failure);
        default void muteAudio() { }
        default void restoreAudio() { }
        default void completed(String reference) { }
    }
    public record Target(String identity, int slot) {
        public Target { ClassCharacter.text(identity, 128); if (slot < 0) throw new IllegalArgumentException("Invalid slot"); }
    }
    private final Actions actions;
    private State state = State.IDLE;
    private UUID account;
    private String target;
    private long started;
    private boolean captured;
    private boolean silenced;
    private int selectedContainer = -1;
    private boolean menuClosed;

    public ClassSwitchController(Actions actions) { this.actions = java.util.Objects.requireNonNull(actions); }
    public State state() { return state; }
    public boolean active() { return state != State.IDLE; }

    public boolean begin(UUID account, String target, String current, boolean verifiedAdapter, long now) {
        if (active()) return false;
        if (!verifiedAdapter || target.equals(current)) return false;
        this.account = java.util.Objects.requireNonNull(account);
        this.target = ClassCharacter.text(target, 128);
        started = now;
        state = State.CAPTURING;
        try {
            silenced = true;
            actions.muteAudio();
            captured = true;
            actions.captureFrame();
        } catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
        return active();
    }

    /** Called after the selected frame has actually rendered and its GPU copy is complete/ordered. */
    public void frameCaptured() {
        if (state != State.CAPTURING) return;
        state = State.WAITING_STAGE;
        try { actions.sendClassCommand(); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    public void selectionStage() {
        if (state != State.WAITING_STAGE) return;
        state = State.WAITING_MENU;
        try { actions.rightClickSelectionStage(); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    /** Wynncraft's Auto Open option can supply the book without requiring a right-click. */
    public boolean bookOpened() {
        if (state == State.WAITING_STAGE) state = State.WAITING_MENU;
        return state == State.WAITING_MENU;
    }

    /** Only a complete, recognized live menu may resolve a target. Duplicate slots also fail closed. */
    public void menu(int containerId, List<Target> targets, boolean complete) {
        if (state != State.WAITING_MENU || !complete) return;
        var matches = targets.stream().filter(t -> t.identity.equals(target)).toList();
        if (containerId < 0 || matches.size() != 1
                || targets.stream().map(Target::slot).distinct().count() != targets.size()
                || targets.stream().map(Target::identity).distinct().count() != targets.size()) {
            fail(Failure.AMBIGUOUS_TARGET);
            return;
        }
        state = State.WAITING_TRANSITION;
        selectedContainer = containerId;
        try { actions.selectCharacter(containerId, matches.getFirst().slot); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    public void menuClosed(int containerId) {
        if (!active()) return;
        if (state == State.WAITING_WORLD && containerId == selectedContainer) return;
        if (state != State.WAITING_TRANSITION || containerId != selectedContainer) {
            fail(Failure.UNEXPECTED_MENU);
            return;
        }
        menuClosed = true;
    }
    public boolean waitingForBlack() { return state == State.WAITING_TRANSITION && menuClosed; }

    /** Actual underlying black frame after the server closes the exact book we selected from.
     * This observes the handoff; it is not an assertion of a server-provided character UUID. */
    public void transition(String selectedIdentity) {
        if (!waitingForBlack()) return;
        if (!target.equals(selectedIdentity)) { fail(Failure.AMBIGUOUS_TARGET); return; }
        state = State.WAITING_WORLD;
        releaseCapturedFrame(); // Reveal Wynncraft's existing black transition; keep audio/input gated.
    }

    /** A nonblack live gameplay frame after that handoff completes the switch and unmutes audio. */
    public void worldReady() {
        if (state != State.WAITING_WORLD) return;
        String completed = target;
        finish();
        actions.completed(completed);
    }

    public void tick(UUID account, boolean connected, long now) {
        if (!active()) return;
        if (!connected) fail(Failure.DISCONNECTED);
        else if (!this.account.equals(account)) fail(Failure.ACCOUNT_CHANGED);
        else if (now - started >= TIMEOUT_MILLIS) fail(Failure.TIMEOUT);
    }
    public void unexpectedMenu() { if (active()) fail(Failure.UNEXPECTED_MENU); }
    public void cancel() { if (active()) fail(Failure.CANCELLED); }
    public void unavailable() { if (active()) fail(Failure.UNAVAILABLE); }
    private void fail(Failure failure) {
        try { finish(); }
        finally { actions.failed(failure); }
    }
    private void finish() {
        state = State.IDLE;
        target = null;
        account = null;
        selectedContainer = -1;
        menuClosed = false;
        try { releaseCapturedFrame(); }
        finally { if (silenced) { silenced = false; actions.restoreAudio(); } }
    }
    private void releaseCapturedFrame() { if (captured) { captured = false; actions.releaseFrame(); } }
}
