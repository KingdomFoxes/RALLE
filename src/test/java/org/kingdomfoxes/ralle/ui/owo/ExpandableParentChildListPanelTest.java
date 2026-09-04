package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpandableParentChildListPanelTest {
    @Test void replacingRowsInvalidatesLayoutOnlyAfterTheCompleteProjectionExists() {
        var list = new TrackingList();
        list.replaceChildren(rows(14));
        list.resetTracking();
        list.replaceChildren(rows(16));

        assertEquals(1, list.layoutUpdates);
        assertEquals(List.of(16), list.observedChildCounts);
    }

    private static List<UIComponent> rows(int count) {
        var rows = new ArrayList<UIComponent>();
        for (int index = 0; index < count; index++) {
            rows.add(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24)));
        }
        return rows;
    }

    private static final class TrackingList extends ExpandableParentChildListPanel {
        private int layoutUpdates;
        private final List<Integer> observedChildCounts = new ArrayList<>();

        private TrackingList() {
            super(Sizing.fill(100), Sizing.content());
        }

        @Override protected void updateLayout() {
            layoutUpdates++;
            if (observedChildCounts != null) observedChildCounts.add(children().size());
            super.updateLayout();
        }

        private void resetTracking() {
            layoutUpdates = 0;
            observedChildCounts.clear();
        }
    }
}
