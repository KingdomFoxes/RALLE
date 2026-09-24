package org.kingdomfoxes.ralle.war;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Exact server-message trigger; never routes through the editable chat draft or tabs. */
public final class PointAndLaugh {
    // Original system-chat payload observed in MultiMC 2026-09-20/21 logs:
    // negative spacing U+CFFFC, notification icon U+E001, spacing U+D0006, then one space.
    // Match the entire payload so player/channel prefixes cannot turn quotes into triggers.
    private static final String WAR_MESSAGE = "\uDAFF\uDFFC\uE001\uDB00\uDC06 Nobody logged in for the war.";
    // Red (&c) continuation line in 2026-09-20-2.log.gz:7981-7982.
    // Other channels share these glyphs, but their rank/sender/body must never be stripped.
    private static final String WAR_CONTINUATION =
            "\uDAFF\uDFFC\uE006\uDAFF\uDFFF\uE002\uDAFF\uDFFE Nobody logged in for the war.";
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private long lastSent;
    private boolean sent;

    public void observe(String text, boolean onWynncraft, boolean overlay, String reply,
                        long nowMillis, Consumer<String> sendCommand) {
        if (!onWynncraft || overlay || reply.isBlank()) return;
        var plain = FORMATTING.matcher(text).replaceAll("");
        if (!plain.equals(WAR_MESSAGE) && !plain.equals(WAR_CONTINUATION)) return;
        // Bound duplicate server deliveries without queuing or retrying commands.
        if (sent && nowMillis - lastSent < 1_000) return;
        sent = true;
        lastSent = nowMillis;
        sendCommand.accept("g " + reply);
    }

    public void reset() { sent = false; }
}
