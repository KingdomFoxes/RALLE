package org.kingdomfoxes.ralle.classes;

import java.util.Collection;
import java.util.List;

/** Shared fresh-press/release and mouse submission guard; identities are captured on press. */
public final class ClassSelectorInput {
    private String selected;
    private String armedKey;
    private final java.util.Set<String> held = new java.util.HashSet<>();
    private boolean finished;

    public void begin(Collection<String> alreadyHeld) {
        held.clear();
        held.addAll(alreadyHeld);
        selected = null;
        armedKey = null;
        finished = false;
    }

    public static boolean conflict(String binding, String modifier, List<ClassShortcut> shortcuts,
                                   Collection<String> reserved) {
        return !ClassShortcut.UNBOUND.equals(binding) && (binding.equals(modifier) || reserved.contains(binding)
                || shortcuts.stream().filter(s -> s.binding().equals(binding)).count() > 1);
    }
    public String selected() { return selected; }
    public boolean finished() { return finished; }
    public void press(String key, String identity) {
        if (finished || !held.add(key)) return;
        selected = identity;
        armedKey = key;
    }
    public String release(String key, boolean modifierHeld) {
        held.remove(key);
        if (finished || !key.equals(armedKey)) return null;
        String result = modifierHeld ? selected : null;
        finished = !modifierHeld || selected != null;
        selected = null;
        armedKey = null;
        return result;
    }
    public String click(String identity, boolean fresh) {
        if (finished || !fresh || identity == null) return null;
        finished = true;
        selected = null;
        armedKey = null;
        return identity;
    }
    public void cancel() { finished = true; selected = null; armedKey = null; }
}
