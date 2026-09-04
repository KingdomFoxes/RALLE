package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Reusable vertical parent/child list panel whose expansion is intentionally session-local. */
public class ExpandableParentChildListPanel extends FlowLayout {
    private final Set<Integer> expanded;

    public ExpandableParentChildListPanel(Sizing horizontalSizing, Sizing verticalSizing) {
        this(horizontalSizing, verticalSizing, new HashSet<>());
    }

    public ExpandableParentChildListPanel(Sizing horizontalSizing, Sizing verticalSizing, Set<Integer> expanded) {
        super(horizontalSizing, verticalSizing, Algorithm.VERTICAL);
        this.expanded = expanded;
    }

    public boolean expanded(int index) { return expanded.contains(index); }

    public void toggle(int index) {
        if (!expanded.add(index)) expanded.remove(index);
    }

    public void forgetAtOrAfter(int index) {
        expanded.removeIf(value -> value >= index);
    }

    /** Replaces the complete projection with one layout pass, preserving parent scroll state. */
    public void replaceChildren(Collection<? extends UIComponent> replacements) {
        for (var child : children) {
            child.dismount(DismountReason.REMOVED);
        }
        children.clear();
        children.addAll(replacements);
        updateLayout();
    }
}
