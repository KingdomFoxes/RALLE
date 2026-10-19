package org.kingdomfoxes.ralle.classes;

/** Server prompt can arrive during the initial blackout. Require the actual black-to-scene
 * observation before submitting the native Use click; bright pre-command gameplay is insufficient. */
public final class ClassStageReadiness {
    private boolean prompt, sawBlack, visible;
    public void prompt() { prompt(true); }
    public void prompt(boolean present) { prompt = present; }
    public void frame(boolean black, boolean usableScene) {
        if (black) { sawBlack = true; visible = false; }
        else visible = sawBlack && usableScene;
    }
    public boolean ready() { return prompt && visible; }
    public void reset() { prompt = sawBlack = visible = false; }
}
