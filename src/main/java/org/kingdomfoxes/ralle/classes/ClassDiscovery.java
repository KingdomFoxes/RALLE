package org.kingdomfoxes.ralle.classes;

import java.io.IOException;
import java.util.UUID;

/** Game-data scanner boundary. Does no network lookup and never opens a menu to discover characters. */
public final class ClassDiscovery {
    private final ClassSelectorStore store;
    private UUID account;
    private boolean enabled;
    private String current;
    private long generation;

    public ClassDiscovery(ClassSelectorStore store) { this.store = java.util.Objects.requireNonNull(store); }
    public void session(UUID account, boolean enabled) {
        if (!java.util.Objects.equals(this.account, account) || this.enabled != enabled) {
            this.account = account;
            this.enabled = enabled;
            current = null;
            generation++;
        }
    }
    public long generation() { return generation; }
    public String current() { return current; }

    /** Reject delayed callbacks from prior connections, accounts or disabled feature generations. */
    public boolean observe(ClassScan scan, long generation) throws IOException {
        if (!enabled || this.generation != generation || !scan.account().equals(account)) return false;
        store.reconcile(scan);
        return true;
    }

    public void selected(UUID account, String identity, long generation) {
        if (enabled && this.generation == generation && java.util.Objects.equals(this.account, account))
            current = ClassCharacter.text(identity, 128);
    }

    public void disconnect() { account = null; current = null; generation++; }
}
