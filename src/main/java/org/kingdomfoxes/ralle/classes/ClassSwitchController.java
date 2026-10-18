package org.kingdomfoxes.ralle.classes;

import java.util.List;
import java.util.UUID;

/** Single bounded operation; all stages advance from observations, never sleeps or retries. */
public final class ClassSwitchController {
    public static final long TIMEOUT_MILLIS = 15_000;
    public enum State { IDLE, WAITING_STAGE, WAITING_MENU, WAITING_TRANSITION }
    public enum Failure { CANCELLED, TIMEOUT, DISCONNECTED, UNEXPECTED_MENU, AMBIGUOUS_TARGET, ACCOUNT_CHANGED, UNAVAILABLE }

    /** Implement only after verifying the server's stage, stable identity and transition signals. */
    public interface Actions {
        void captureFrame();
        void sendClassCommand();
        void rightClickSelectionStage();
        void selectCharacter(int containerId, int slot);
        void releaseFrame();
        void failed(Failure failure);
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

    public ClassSwitchController(Actions actions) { this.actions = java.util.Objects.requireNonNull(actions); }
    public State state() { return state; }
    public boolean active() { return state != State.IDLE; }

    public boolean begin(UUID account, String target, String current, boolean verifiedAdapter, long now) {
        if (active()) return false;
        if (!verifiedAdapter || target.startsWith(ClassCharacter.OBSERVATION_PREFIX) || target.equals(current)) return false;
        this.account = java.util.Objects.requireNonNull(account);
        this.target = ClassCharacter.text(target, 128);
        started = now;
        state = State.WAITING_STAGE;
        try {
            captured = true;
            actions.captureFrame();
            actions.sendClassCommand();
        } catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
        return active();
    }

    public void selectionStage() {
        if (state != State.WAITING_STAGE) return;
        state = State.WAITING_MENU;
        try { actions.rightClickSelectionStage(); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
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
        try { actions.selectCharacter(containerId, matches.getFirst().slot); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    /** Adapter calls at the verified black-to-world boundary, with the actual selected identity. */
    public void transition(String selectedIdentity) {
        if (state != State.WAITING_TRANSITION) return;
        if (!target.equals(selectedIdentity)) { fail(Failure.AMBIGUOUS_TARGET); return; }
        finish();
    }

    public void tick(UUID account, boolean connected, long now) {
        if (!active()) return;
        if (!connected) fail(Failure.DISCONNECTED);
        else if (!this.account.equals(account)) fail(Failure.ACCOUNT_CHANGED);
        else if (now - started >= TIMEOUT_MILLIS) fail(Failure.TIMEOUT);
    }
    public void unexpectedMenu() { if (active()) fail(Failure.UNEXPECTED_MENU); }
    public void cancel() { if (active()) fail(Failure.CANCELLED); }
    private void fail(Failure failure) {
        try { finish(); }
        finally { actions.failed(failure); }
    }
    private void finish() {
        state = State.IDLE;
        target = null;
        account = null;
        if (captured) { captured = false; actions.releaseFrame(); }
    }
}
