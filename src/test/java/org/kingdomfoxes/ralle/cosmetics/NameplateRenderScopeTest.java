package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameplateRenderScopeTest {
    private final NameplateStyle style = NameplateStyle.byId("supporter-gold").orElseThrow();
    private final CosmeticAppearance appearance = new CosmeticAppearance(1, CosmeticAppearance.Treatment.PLATE, 0);

    @Test void replacementIconAndServerSuffixUseTheCompleteFinalUsernameRow() {
        var original = Component.literal("§fPlayer");
        var score = Component.literal("123 points");
        var finalLabel = Component.empty().append(Component.literal("\ue001 "))
                .append(original).append(Component.literal(" [Fox]"));
        try (var scope = NameplateRenderScope.open(style, appearance, "Player", original, score, null)) {
            assertTrue(scope.decorates(original));
            assertTrue(scope.decorates(finalLabel));
            assertFalse(scope.decorates(score));
            assertFalse(scope.decorates(Component.literal("Lv 10")));
            assertFalse(scope.decorates(Component.literal("PlayerTwo")));
            assertFalse(scope.decorates(Component.literal("OtherPlayer")));
            var font = CosmeticTestFont.create();
            var tinted = LiquidComponentTint.apply(finalLabel, font, (x, y) -> 0xffffffff);
            var full = WorldNameplateBounds.measure(font, tinted, 0);
            var early = WorldNameplateBounds.measure(font, original, 0);
            assertTrue(full.right() - full.left() > early.right() - early.left());
            assertEquals("\ue001 Player [Fox]", tinted.getString());
        }
        assertNull(NameplateRenderScope.current());
    }

    @Test void disabledNestedPlayerCannotInheritAnotherPlayersCosmeticAndFailureRestoresTheScope() {
        var label = Component.literal("Player");
        try (var outer = NameplateRenderScope.open(style, appearance, "Player", label, null, null)) {
            assertSame(outer, NameplateRenderScope.current());
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = NameplateRenderScope.open(null, null, "Other", Component.literal("Other"), null, null)) {
                    assertFalse(inner.decorates(label));
                    throw new IllegalStateException("Failed replacement renderer");
                }
            });
            assertSame(outer, NameplateRenderScope.current());
            assertTrue(outer.decorates(label));
        }
        assertNull(NameplateRenderScope.current());
    }

    @Test void laterCollectionSubmissionCannotRecurseOrDuplicateDecoration() {
        var original = Component.literal("Player");
        var finalLabel = Component.literal("\ue001 Player");
        try (var scope = NameplateRenderScope.open(style, appearance, "Player", original, null, null)) {
            int[] submitted = {0};
            assertTrue(scope.decorates(finalLabel));
            scope.submit(finalLabel, () -> {
                submitted[0]++;
                assertSame(finalLabel, scope.submittingLabel());
                assertFalse(scope.decorates(finalLabel));
            });
            assertEquals(1, submitted[0]);
            assertNull(scope.submittingLabel());
            assertThrows(IllegalStateException.class, () -> scope.submit(finalLabel, () -> {
                throw new IllegalStateException("Failed submission");
            }));
            assertNull(scope.submittingLabel());
            assertTrue(scope.decorates(finalLabel));
        }
    }
}
