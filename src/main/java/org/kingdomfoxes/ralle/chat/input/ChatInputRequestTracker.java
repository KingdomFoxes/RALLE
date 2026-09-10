package org.kingdomfoxes.ralle.chat.input;

/** Correlates a particular input-menu action with its server acknowledgement. No text is sent. */
public final class ChatInputRequestTracker {
    private static final long ACK_WINDOW_MS = 5_000;
    private final ChatTypeTabService tabs;
    private int pendingContainer = -1;
    private long deadline;

    public ChatInputRequestTracker(ChatTypeTabService tabs) {
        this.tabs = tabs;
    }

    public void observePrompt(String message) {
        if (WynncraftInputPrompts.isPrompt(message)) {
            reset();
            tabs.selectAllForInput();
        } else if (WynncraftInputPrompts.isCancellation(message)) {
            reset();
        }
    }

    public void menuAction(int container, String title, String name, String lore, long now) {
        reset();
        if (container > 0 && WynncraftInputPrompts.isInputAction(title, name, lore)) {
            pendingContainer = container;
            deadline = now + ACK_WINDOW_MS;
        }
    }

    public void serverClosed(int container, long now) {
        boolean accepted = pendingContainer > 0 && container == pendingContainer && now <= deadline;
        reset();
        if (accepted) tabs.selectAllForInput();
    }

    public void tick(long now) {
        if (now > deadline) reset();
    }

    public void reset() {
        pendingContainer = -1;
        deadline = 0;
    }
}
