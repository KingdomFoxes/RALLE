package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnNameTagPreviewTest {
    @Test void inventoryScopeRestoresNestedStateAndClearsAfterFailure() {
        assertFalse(OwnNameTagPreview.active());
        assertThrows(IllegalStateException.class, () -> OwnNameTagPreview.extract(() -> {
            assertTrue(OwnNameTagPreview.active());
            OwnNameTagPreview.extract(() -> {
                assertTrue(OwnNameTagPreview.active());
                return null;
            });
            assertTrue(OwnNameTagPreview.active());
            throw new IllegalStateException("Failed extraction");
        }));
        assertFalse(OwnNameTagPreview.active());
    }

    @Test void inventoryViewportFitsNameAndKeepsCharacterCentered() {
        var original = new InventoryNameTagBounds(98, 45, 147, 115);
        var fitted = original.fit(160, 30, 320, 240);
        assertEquals(original.left() + original.right(), fitted.left() + fitted.right());
        assertEquals(original.top() + original.bottom(), fitted.top() + fitted.bottom());
        assertTrue(fitted.right() - fitted.left() >= 126);
        assertTrue(fitted.top() <= original.top() - 24);
        assertTrue(fitted.bottom() >= original.bottom() + 24);
    }

    @Test void hostileLongLabelsNeverGrowViewportOutsideScreen() {
        var original = new InventoryNameTagBounds(26, 8, 75, 78);
        var fitted = original.fit(100_000, 30, 320, 240);
        assertTrue(fitted.left() >= 0);
        assertTrue(fitted.top() >= 0);
        assertTrue(fitted.right() <= 320);
        assertTrue(fitted.bottom() <= 240);
        assertEquals(original.left() + original.right(), fitted.left() + fitted.right());
        assertEquals(original.top() + original.bottom(), fitted.top() + fitted.bottom());
    }
}
