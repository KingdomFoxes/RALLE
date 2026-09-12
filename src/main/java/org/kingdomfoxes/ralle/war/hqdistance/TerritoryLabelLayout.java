package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.Optional;

/** Native-size labels anchored to the guild tag, allowed to overflow small territories. */
public final class TerritoryLabelLayout {
    private static final int GAP = 3;
    private static final int HQ_CROWN_HEIGHT = 13;

    private TerritoryLabelLayout() {}

    public static Optional<Layout> calculate(
            TerritoryRenderBounds bounds,
            int upperWidth,
            int lowerWidth,
            int lineHeight,
            boolean headquarters,
            boolean lowerLabel
    ) {
        int left = Math.round(bounds.left());
        int top = Math.round(bounds.top());
        int width = Math.round(bounds.width());
        int height = Math.round(bounds.height());
        if (bounds.width() <= 0 || bounds.height() <= 0 || lineHeight <= 0) return Optional.empty();

        int upperY;
        int lowerY = -1;
        if (headquarters) {
            int crownTop = top + (height - HQ_CROWN_HEIGHT) / 2;
            upperY = crownTop - lineHeight - GAP;
            if (lowerLabel) lowerY = crownTop + HQ_CROWN_HEIGHT + GAP;
        } else {
            int tagTop = top + (height - lineHeight) / 2;
            upperY = tagTop - lineHeight - GAP;
            if (lowerLabel) lowerY = tagTop + lineHeight + GAP;
        }

        return Optional.of(new Layout(left + width / 2, upperY, lowerY));
    }

    public record Layout(int centerX, int upperY, int lowerY) {}
}
