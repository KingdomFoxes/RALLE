package org.kingdomfoxes.ralle.cosmetics;

import java.util.function.Supplier;

/** Scopes inventory extraction so opening inventory never enables a first-person world tag. */
public final class OwnNameTagPreview {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private OwnNameTagPreview() {}

    public static boolean active() { return ACTIVE.get(); }

    public static <T> T extract(Supplier<T> extraction) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return extraction.get();
        } finally {
            if (previous) ACTIVE.set(true);
            else ACTIVE.remove();
        }
    }
}
