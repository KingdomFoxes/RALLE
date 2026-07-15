package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.util.FormattedCharSequence;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.List;

public interface ChatScreenshotCapture {
    void capture(Request request, Completion completion);

    record Request(
            List<FormattedCharSequence> lines,
            int visualWidth,
            int lineHeight,
            int textBaselineOffset,
            double chatScale,
            float textOpacity,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow
    ) {
        public Request {
            lines = List.copyOf(lines);
        }
    }

    interface Completion {
        void succeeded();
        void failed(Throwable error);
    }
}
