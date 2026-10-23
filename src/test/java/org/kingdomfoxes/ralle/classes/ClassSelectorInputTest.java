package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassSelectorInputTest {
    @Test void latestFreshPressWinsAndRepeatedOrOlderReleasesCannotSubmit() {
        var input = new ClassSelectorInput();
        input.press("g", "first"); input.press("h", "second"); input.press("g", "first");
        assertEquals("second", input.selected());
        assertNull(input.release("g", true));
        assertEquals("second", input.release("h", true));
        assertNull(input.release("h", true));
        assertNull(input.click("first", true));
    }
    @Test void preheldKeysFreshClicksAndModifierCancellationShareOneGuard() {
        var input = new ClassSelectorInput();
        input.begin(List.of("g")); input.press("g", "first");
        assertNull(input.selected());
        input.release("g", true); input.press("g", "first");
        assertNull(input.click("first", false));
        assertEquals("first", input.click("first", true));
        assertNull(input.release("g", true));
        input.begin(List.of()); input.press("g", "first");
        assertNull(input.release("g", false));
        input.begin(List.of()); input.press("g", "first"); input.cancel();
        assertNull(input.release("g", true));
    }
    @Test void modifierDuplicateOptionsAndOtherActionBindingsAreConflicts() {
        var a = new ClassShortcut("a", "", "key.keyboard.g");
        var b = new ClassShortcut("b", "", "key.keyboard.g");
        assertTrue(ClassSelectorInput.conflict(a.binding(), "key.keyboard.h", List.of(a, b), List.of()));
        assertTrue(ClassSelectorInput.conflict(a.binding(), a.binding(), List.of(a), List.of()));
        assertTrue(ClassSelectorInput.conflict(a.binding(), "unbound", List.of(a), List.of(a.binding())));
        assertFalse(ClassSelectorInput.conflict(a.binding(), "key.keyboard.h", List.of(a), List.of()));
        assertFalse(ClassSelectorInput.conflict("unbound", "unbound", List.of(a), List.of("unbound")));
    }
}
