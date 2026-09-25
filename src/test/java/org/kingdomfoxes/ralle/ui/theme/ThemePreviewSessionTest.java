package org.kingdomfoxes.ralle.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemePreviewSessionTest {
    @Test void editsAreIndependentAndDoNotMutateCatalog() {
        var before = RalleThemeCatalog.get("joker");
        var session = new ThemePreviewSession();
        session.setColor("joker", ThemePreviewSession.Role.BACKGROUND, 0x0000FF);
        session.setColor("coral-castle", ThemePreviewSession.Role.ACCENT, 0x123456);
        assertEquals(0x0000FF, session.effectiveTheme("joker").background());
        assertEquals(0x123456, session.effectiveTheme("coral-castle").accent());
        assertEquals(before, RalleThemeCatalog.get("joker"));
        assertEquals(before.name(), session.effectiveTheme("joker").name());
        assertEquals(before.contributor(), session.effectiveTheme("joker").contributor());
        assertEquals(before.outline(), session.effectiveTheme("joker").outline());
        assertEquals(before.accent(), session.effectiveTheme("joker").accent());
    }

    @Test void freshSessionUsesPackagedColorsAndRevisionOnlyChangesOnEffectiveEdit() {
        var session = new ThemePreviewSession();
        var catalog = RalleThemeCatalog.get("joker");
        assertEquals(catalog, session.effectiveTheme("joker"));
        assertEquals(0, session.revision());
        assertTrue(session.setColor("joker", ThemePreviewSession.Role.ACCENT, 0x010203));
        assertEquals(1, session.revision());
        assertFalse(session.setColor("joker", ThemePreviewSession.Role.ACCENT, 0x010203));
        assertEquals(1, session.revision());
        assertThrows(IllegalArgumentException.class,
                () -> session.setColor("joker", ThemePreviewSession.Role.ACCENT, 0x1000000));
    }

    @Test void changingDefaultOutlineOrAccentIsDetectedByCompleteTriplet() {
        var session = new ThemePreviewSession();
        assertEquals(0xFFFFFF, session.effectiveTheme("default").outline());
        session.setColor("default", ThemePreviewSession.Role.OUTLINE, 0x010203);
        assertEquals(0x010203, session.effectiveTheme("default").outline());
        assertFalse(RallePalette.usesLegacyDefaultShades(session.effectiveTheme("default")));
        session.setColor("default", ThemePreviewSession.Role.ACCENT, 0x040506);
        assertEquals(0x040506, session.effectiveTheme("default").accent());
        assertFalse(RallePalette.usesLegacyDefaultShades(session.effectiveTheme("default")));
        assertTrue(RallePalette.usesLegacyDefaultShades(RalleThemeCatalog.get("default")));
    }

    @Test void editsRemainAvailableWhenSwitchingAwayAndBack() {
        var session = new ThemePreviewSession();
        session.setColor("joker", ThemePreviewSession.Role.BACKGROUND, 0x123456);
        assertEquals(RalleThemeCatalog.get("coral-castle"), session.effectiveTheme("coral-castle"));
        assertEquals(0x123456, session.effectiveTheme("joker").background());
        assertEquals(RalleThemeCatalog.get("joker").name(), session.effectiveTheme("joker").name());
    }
}
