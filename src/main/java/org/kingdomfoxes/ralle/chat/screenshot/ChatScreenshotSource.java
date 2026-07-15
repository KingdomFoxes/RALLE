package org.kingdomfoxes.ralle.chat.screenshot;

/** Narrow bridge implemented by the chat component mixin. */
public interface ChatScreenshotSource {
    ChatScreenshotSnapshot ralle$freezeScreenshotMessages();
    int ralle$screenshotScroll();
    void ralle$selectionAutoscroll(int amount);
}
