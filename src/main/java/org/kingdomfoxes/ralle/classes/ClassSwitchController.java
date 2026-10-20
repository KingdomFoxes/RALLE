package org.kingdomfoxes.ralle.classes;

import java.util.List;
import java.util.UUID;

/** Bounded command/book recovery; every character click requires a freshly validated book. */
public final class ClassSwitchController {
    public static final long TIMEOUT_MILLIS = 15_000;
    public static final long STAGE_CLICK_INTERVAL_MILLIS = 100;
    public static final int MAX_STAGE_CLICKS = 150;
    public static final long ANIMATION_MILLIS = 1600;
    public static final long COMMAND_RETRY_MILLIS = 750;
    public static final int MAX_COMMAND_ATTEMPTS = 3;
    public static final long BOOK_CONTENT_TIMEOUT_MILLIS = 600;
    public static final long BOOK_SELECTION_TIMEOUT_MILLIS = 800;
    public static final int MAX_BOOK_RECOVERIES = 2;
    public enum State { IDLE, CAPTURING, WAITING_STAGE, WAITING_MENU, WAITING_TRANSITION, WAITING_WORLD }
    public enum Failure { CANCELLED, TIMEOUT, DISCONNECTED, UNEXPECTED_MENU, AMBIGUOUS_TARGET, ACCOUNT_CHANGED, UNAVAILABLE }
    public enum RetryKind { COMMAND, BOOK }
    public record Retry(RetryKind kind, int number, int total) { }

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
        /** Only usable gameplay, before any selection-stage/book evidence. */
        default boolean canRetryClassCommand() { return false; }
        /** Close only the still-live character book belonging to this operation. */
        default boolean closeCharacterBook(int containerId) { return false; }
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
    private boolean commandSent;
    private boolean silenced;
    private int selectedContainer = -1;
    private boolean bookReceived;
    private long nextStageClick;
    private int stageClicks;
    private long animationEnds;
    private long observedNow, commandAt, bookAt;
    private int commandAttempts, bookRecoveries, openedContainer = -1;
    private boolean stageObserved;
    private Retry retry;
    private final java.util.Set<Integer> retiredBooks = new java.util.LinkedHashSet<>();

    public ClassSwitchController(Actions actions) { this.actions = java.util.Objects.requireNonNull(actions); }
    public State state() { return state; }
    public boolean active() { return state != State.IDLE; }
    public Retry retry() { return state == State.WAITING_WORLD ? null : retry; }

    public boolean begin(UUID account, String target, String current, boolean verifiedAdapter, long now) {
        if (active()) return false;
        if (!verifiedAdapter || target.equals(current)) return false;
        this.account = java.util.Objects.requireNonNull(account);
        this.target = ClassCharacter.text(target, 128);
        started = now;
        observedNow = now;
        commandAttempts = bookRecoveries = 0;
        retry = null;
        openedContainer = -1;
        stageObserved = false;
        nextStageClick = now;
        stageClicks = 0;
        bookReceived = false;
        commandSent = false;
        state = State.CAPTURING;
        try {
            silenced = true;
            actions.muteAudio();
            captured = true;
            actions.captureFrame();
        } catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
        return active();
    }

    /** After packet processing and ticks, before drawing this frame. Responses are applied next pass. */
    public void frameRendering() {
        if (state != State.CAPTURING || commandSent) return;
        commandSent = true;
        sendCommand(observedNow);
    }

    private void sendCommand(long now) {
        commandAttempts++;
        if (commandAttempts > 1) retry = new Retry(RetryKind.COMMAND, commandAttempts - 1, MAX_COMMAND_ATTEMPTS - 1);
        commandAt = now;
        try { actions.sendClassCommand(); }
        catch (RuntimeException failure) {
            if (commandAttempts >= MAX_COMMAND_ATTEMPTS) fail(Failure.UNAVAILABLE);
        }
    }

    public void observedSelectionStage() { if (active()) stageObserved = true; }

    /** Stage interaction stays gated until the actually selected frame has been copied. */
    public void frameCaptured() {
        if (state != State.CAPTURING) return;
        frameRendering(); // Safe fallback for adapters without a pre-render callback.
        if (state == State.CAPTURING) state = State.WAITING_STAGE;
    }

    public boolean acceptingStageClicks() {
        return state == State.WAITING_STAGE || state == State.WAITING_MENU && !bookReceived;
    }

    /** Called only with a current recognized prompt and a usable native scene. */
    public void selectionStage(long now) {
        observedNow = now;
        if (!acceptingStageClicks()) return;
        stageObserved = true;
        if (now - started >= TIMEOUT_MILLIS) { fail(Failure.TIMEOUT); return; }
        if (now < nextStageClick || stageClicks >= MAX_STAGE_CLICKS) return;
        state = State.WAITING_MENU;
        stageClicks++;
        nextStageClick = now + STAGE_CLICK_INTERVAL_MILLIS;
        try { actions.rightClickSelectionStage(); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    /** Wynncraft's Auto Open option can supply the book without requiring a right-click. */
    public boolean bookOpened() {
        return bookOpened(-1, observedNow);
    }
    public boolean bookOpened(int containerId, long now) {
        observedNow = now;
        if (bookReceived && openedContainer != containerId) return false;
        if (state == State.WAITING_STAGE) state = State.WAITING_MENU;
        if (state == State.WAITING_MENU && !bookReceived) {
            bookReceived = true;
            stageObserved = true;
            openedContainer = containerId;
            retiredBooks.remove(containerId); // Server container IDs may eventually be reused.
            bookAt = now;
        }
        return state == State.WAITING_MENU;
    }

    /** Only a complete, recognized live menu may resolve a target. Duplicate slots also fail closed. */
    public void menu(int containerId, List<Target> targets, boolean complete) {
        if (state != State.WAITING_MENU || !complete) return;
        if (openedContainer >= 0 && containerId != openedContainer) return;
        var matches = targets.stream().filter(t -> t.identity.equals(target)).toList();
        if (containerId < 0 || matches.size() != 1
                || targets.stream().map(Target::slot).distinct().count() != targets.size()
                || targets.stream().map(Target::identity).distinct().count() != targets.size()) {
            fail(Failure.AMBIGUOUS_TARGET);
            return;
        }
        state = State.WAITING_TRANSITION;
        selectedContainer = containerId;
        openedContainer = containerId;
        bookAt = observedNow;
        try { actions.selectCharacter(containerId, matches.getFirst().slot); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); }
    }

    /** Vanilla closes its live menu regardless of the packet ID. A zero-ID close after
     * selection may also follow another mod locally dismissing that same book. */
    public void serverClosed(int packetContainer, int liveContainer, long now) {
        if (retiredBooks.contains(packetContainer)) return;
        // Wynntils can finish a previous world-entry query after the next /class. Its
        // server close names a non-book container already dismissed locally. With only
        // inventory live and no book received/selected, it cannot close this operation.
        if (liveContainer == 0 && !bookReceived && selectedContainer < 0) return;
        if (state == State.WAITING_MENU && bookReceived && liveContainer == openedContainer) {
            menuClosed(openedContainer, now);
            return;
        }
        int closed = selectedContainer >= 0 && (liveContainer == selectedContainer
                || packetContainer == 0 && liveContainer == 0) ? selectedContainer : packetContainer;
        menuClosed(closed, now);
    }
    public void menuClosed(int containerId, long now) {
        if (!active()) return;
        if (retiredBooks.contains(containerId)) return;
        if (containerId == 0 && selectedContainer < 0) return;
        if (state == State.WAITING_WORLD) return; // Later closes cannot restart the animation timer.
        if (state == State.WAITING_MENU && bookReceived && containerId == openedContainer) {
            recoverBook(now, false);
            return;
        }
        if (state != State.WAITING_TRANSITION || containerId != selectedContainer) {
            fail(Failure.UNEXPECTED_MENU);
            return;
        }
        state = State.WAITING_WORLD;
        animationEnds = now + ANIMATION_MILLIS;
    }

    /** The user-approved door duration bounds frame, audio and input ownership. */
    private void worldReady() {
        if (state != State.WAITING_WORLD) return;
        String completed = target;
        finish();
        actions.completed(completed);
    }

    public void tick(UUID account, boolean connected, long now) {
        observedNow = now;
        if (!active()) return;
        if (!connected) fail(Failure.DISCONNECTED);
        else if (!this.account.equals(account)) fail(Failure.ACCOUNT_CHANGED);
        else if (now - started >= TIMEOUT_MILLIS) fail(Failure.TIMEOUT);
        else if (state == State.WAITING_WORLD && now >= animationEnds) worldReady();
        else if (state == State.WAITING_STAGE && !stageObserved && commandAttempts < MAX_COMMAND_ATTEMPTS
                && now - commandAt >= COMMAND_RETRY_MILLIS && actions.canRetryClassCommand()) sendCommand(now);
        else if (openedContainer >= 0 && (state == State.WAITING_MENU && bookReceived
                && now - bookAt >= BOOK_CONTENT_TIMEOUT_MILLIS
                || state == State.WAITING_TRANSITION && now - bookAt >= BOOK_SELECTION_TIMEOUT_MILLIS)) recoverBook(now, true);
    }

    private void recoverBook(long now, boolean close) {
        if (bookRecoveries >= MAX_BOOK_RECOVERIES) { fail(Failure.TIMEOUT); return; }
        int oldBook = openedContainer;
        bookRecoveries++;
        retry = new Retry(RetryKind.BOOK, bookRecoveries, MAX_BOOK_RECOVERIES);
        retireBook(oldBook);
        selectedContainer = openedContainer = -1;
        bookReceived = false;
        state = State.WAITING_STAGE;
        // Preserve the global deadline, click cap, and 100 ms spacing across every retry.
        nextStageClick = Math.max(nextStageClick, now);
        boolean closed;
        try { closed = !close || actions.closeCharacterBook(oldBook); }
        catch (RuntimeException failure) { fail(Failure.UNAVAILABLE); return; }
        if (!closed) fail(Failure.UNEXPECTED_MENU);
    }
    public boolean retiredBook(int containerId) { return retiredBooks.contains(containerId); }
    private void retireBook(int containerId) {
        if (containerId < 1) return;
        retiredBooks.add(containerId);
        if (retiredBooks.size() > 16) retiredBooks.remove(retiredBooks.iterator().next());
    }
    public void unexpectedMenu() { if (active()) fail(Failure.UNEXPECTED_MENU); }
    public void cancel() { if (active()) fail(Failure.CANCELLED); }
    public void unavailable() { if (active()) fail(Failure.UNAVAILABLE); }
    private void fail(Failure failure) {
        try { finish(); }
        finally { actions.failed(failure); }
    }
    private void finish() {
        retireBook(openedContainer);
        state = State.IDLE;
        retry = null;
        target = null;
        account = null;
        selectedContainer = -1;
        bookReceived = false;
        try { releaseCapturedFrame(); }
        finally { if (silenced) { silenced = false; actions.restoreAudio(); } }
    }
    private void releaseCapturedFrame() { if (captured) { captured = false; actions.releaseFrame(); } }
}
