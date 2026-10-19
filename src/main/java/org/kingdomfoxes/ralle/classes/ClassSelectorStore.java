package org.kingdomfoxes.ralle.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Account-scoped immutable snapshots. Save first, then publish; scans never discard shortcut preferences. */
public final class ClassSelectorStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private Map<UUID, Account> accounts;
    private Map<UUID, Loaded> loads = new LinkedHashMap<>();
    private long revision;

    public record Account(List<ClassCharacter> characters, List<ClassShortcut> shortcuts) {
        public Account {
            characters = List.copyOf(characters);
            shortcuts = List.copyOf(shortcuts);
            if (characters.size() > 64 || shortcuts.size() > 64) throw new IllegalArgumentException("Too many classes");
            if (characters.stream().map(ClassCharacter::id).distinct().count() != characters.size()
                    || shortcuts.stream().map(ClassShortcut::characterId).distinct().count() != shortcuts.size())
                throw new IllegalArgumentException("Duplicate character identity");
        }
        public ClassCharacter character(String id) {
            return characters.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
        }
    }
    private record Loaded(String lastReference, Map<String, String> references) {
        private Loaded {
            lastReference = ClassCharacter.text(lastReference, 128);
            references = Map.copyOf(references);
            if (references.size() > 64) throw new IllegalArgumentException("Too many loaded characters");
            references.forEach((token, reference) -> {
                if (!ClassLoadSignals.validToken(token)) throw new IllegalArgumentException("Invalid compass token");
                if (!reference.isEmpty()) ClassCharacter.text(reference, 128);
            });
        }
    }
    private record FileData(int version, Map<UUID, Account> accounts, Map<UUID, Loaded> loads) {}

    private ClassSelectorStore() { path = null; accounts = new LinkedHashMap<>(); }

    /** A damaged local file must neither crash chat/LFG nor be overwritten by an empty snapshot. */
    public static ClassSelectorStore open(Path path) {
        try { return new ClassSelectorStore(path); }
        catch (IOException failure) {
            org.slf4j.LoggerFactory.getLogger(ClassSelectorStore.class).warn("Could not load class shortcuts; preserving {}", path, failure);
            return new ClassSelectorStore();
        }
    }

    public boolean available() { return path != null; }
    public boolean hasAccount(UUID account) { return accounts.containsKey(account); }

    public ClassSelectorStore(Path path) throws IOException {
        this.path = path;
        accounts = new LinkedHashMap<>();
        if (!Files.exists(path)) return;
        if (Files.size(path) > 1024 * 1024) throw new IOException("Class selector file exceeds 1 MiB");
        try {
            var data = JSON.fromJson(Files.readString(path), FileData.class);
            if (data == null || data.version != 1 || data.accounts == null || data.accounts.size() > 128)
                throw new IllegalArgumentException("Invalid class selector file");
            data.accounts.forEach((id, account) -> {
                java.util.Objects.requireNonNull(id);
                // Reconstruct nested records to validate even when the serializer bypasses constructors.
                var characters = account.characters.stream().map(c -> new ClassCharacter(c.id(), c.type(), c.nickname(), c.sprite())).toList();
                var shortcuts = account.shortcuts.stream().map(s -> new ClassShortcut(s.characterId(), s.name(), s.binding())).toList();
                accounts.put(id, new Account(characters, shortcuts));
            });
            if (data.loads != null) {
                if (data.loads.size() > 128) throw new IllegalArgumentException("Too many accounts");
                data.loads.forEach((id, loaded) -> loads.put(java.util.Objects.requireNonNull(id),
                        new Loaded(loaded.lastReference, loaded.references)));
            }
        } catch (RuntimeException failure) { throw new IOException("Invalid class selector file", failure); }
    }

    public Account account(UUID account) { return accounts.getOrDefault(java.util.Objects.requireNonNull(account), new Account(List.of(), List.of())); }
    public long revision() { return revision; }

    /** Last confirmed load is history, never proof of the current class on a new connection. */
    public String lastLoaded(UUID account) { return loads.containsKey(account) ? loads.get(account).lastReference : null; }
    public String referenceForToken(UUID account, String token) {
        if (!ClassLoadSignals.validToken(token)) return null;
        var loaded = loads.get(account);
        String reference = loaded == null ? null : loaded.references.get(token);
        return reference == null || reference.isEmpty() ? null : reference;
    }
    public void recordLoad(UUID account, String reference, String token) throws IOException {
        var previous = loads.get(account);
        var references = new LinkedHashMap<String, String>();
        if (previous != null) references.putAll(previous.references);
        if (ClassLoadSignals.validToken(token)) {
            // A short token is not globally unique. Conflicting retained observations disable recovery.
            references.merge(token, reference, (old, incoming) -> old.equals(incoming) ? old : "");
            while (references.size() > 64) references.remove(references.keySet().iterator().next());
        }
        var loaded = new Loaded(reference, references);
        if (loaded.equals(previous)) return;
        var nextLoads = new LinkedHashMap<>(loads);
        nextLoads.put(java.util.Objects.requireNonNull(account), loaded);
        if (nextLoads.size() > 128) throw new IllegalArgumentException("Too many accounts");
        save(accounts, nextLoads);
    }

    /** Upgrade only references proven by complete historical item evidence, never name/slot guesses. */
    public void migrateReferences(UUID account, Map<String, String> aliases) throws IOException {
        migrateReferences(account, aliases, List.of());
    }
    public void migrateReferences(UUID account, Map<String, String> aliases, List<ClassCharacter> proofs) throws IOException {
        if (aliases.isEmpty()) return;
        var previous = account(account);
        var characters = new LinkedHashMap<String, ClassCharacter>();
        for (var character : previous.characters) {
            String id = aliases.getOrDefault(character.id(), character.id());
            characters.putIfAbsent(id, new ClassCharacter(id, character.type(), character.nickname(), character.sprite()));
        }
        var shortcuts = new ArrayList<ClassShortcut>();
        for (var shortcut : previous.shortcuts) {
            String id = aliases.getOrDefault(shortcut.characterId(), shortcut.characterId());
            if (shortcuts.stream().anyMatch(existing -> existing.characterId().equals(id))) return; // Conflicting rows stay untouched.
            shortcuts.add(new ClassShortcut(id, shortcut.name(), shortcut.binding()));
            // A progress refresh may have retired the old catalog hash already. Restore its
            // proven normalized descriptor only when that position is still occupied with the
            // same saved name/type/sprite. Never resurrect a missing/deleted catalog entry.
            // This does not authorize a click: exact complete live-book evidence still must match.
            if (!id.equals(shortcut.characterId()) && !characters.containsKey(id)) {
                var proof = proofs.stream().filter(character -> character.id().equals(id)).findFirst().orElse(null);
                if (proof != null) {
                    var known = characters.values().stream().filter(character ->
                            WynncraftClassMenu.characterNumber(character.id()) == WynncraftClassMenu.characterNumber(proof.id())
                                    && character.type().equals(proof.type()) && character.nickname().equals(proof.nickname())
                                    && java.util.Objects.equals(character.sprite(), proof.sprite())).findFirst().orElse(null);
                    if (known != null) {
                        characters.remove(known.id());
                        characters.put(id, proof);
                    }
                }
            }
        }
        var updated = new Account(List.copyOf(characters.values()), shortcuts);
        var nextLoads = new LinkedHashMap<>(loads);
        var loaded = loads.get(account);
        if (loaded != null) {
            var references = new LinkedHashMap<String, String>();
            loaded.references.forEach((token, reference) -> references.put(token, aliases.getOrDefault(reference, reference)));
            nextLoads.put(account, new Loaded(aliases.getOrDefault(loaded.lastReference, loaded.lastReference), references));
        }
        if (updated.equals(previous) && nextLoads.equals(loads)) return;
        var nextAccounts = new LinkedHashMap<>(accounts);
        nextAccounts.put(account, updated);
        save(nextAccounts, nextLoads);
    }

    public void reconcile(ClassScan scan) throws IOException {
        var previous = account(scan.account());
        var characters = new LinkedHashMap<String, ClassCharacter>();
        if (!scan.complete()) previous.characters.forEach(c -> characters.put(c.id(), c));
        // A partial scan may refresh a position's evidence without proving continuity of identity.
        // Retire its old catalog entry rather than accumulating duplicates or redirecting a shortcut.
        for (var incoming : scan.characters()) {
            int position = WynncraftClassMenu.characterNumber(incoming.id());
            if (position >= 0) characters.values().removeIf(old ->
                    WynncraftClassMenu.characterNumber(old.id()) == position && !old.id().equals(incoming.id()));
            characters.put(incoming.id(), incoming);
        }
        replace(scan.account(), new Account(List.copyOf(characters.values()), previous.shortcuts));
    }

    public void add(UUID account, String id) throws IOException {
        var current = account(account);
        if (current.character(id) == null) throw new IllegalArgumentException("Character is no longer detected");
        if (current.shortcuts.stream().anyMatch(s -> s.characterId().equals(id))) return;
        var shortcuts = new ArrayList<>(current.shortcuts);
        shortcuts.add(new ClassShortcut(id, "", ClassShortcut.UNBOUND));
        replace(account, new Account(current.characters, shortcuts));
    }

    public void edit(UUID account, ClassShortcut shortcut) throws IOException {
        var current = account(account);
        var shortcuts = new ArrayList<>(current.shortcuts);
        int index = -1;
        for (int i = 0; i < shortcuts.size(); i++)
            if (shortcuts.get(i).characterId().equals(shortcut.characterId())) index = i;
        if (index < 0) throw new IllegalArgumentException("Shortcut no longer exists");
        shortcuts.set(index, shortcut);
        replace(account, new Account(current.characters, shortcuts));
    }

    public void remove(UUID account, String id) throws IOException {
        var current = account(account);
        replace(account, new Account(current.characters,
                current.shortcuts.stream().filter(s -> !s.characterId().equals(id)).toList()));
    }

    public void clearShortcuts(UUID account) throws IOException {
        replace(account, new Account(account(account).characters(), List.of()));
    }

    /** Hydrate only matching existing references; historical captures cannot resurrect removed entries. */
    public void updateSprites(UUID account, Map<String, ClassSprite> sprites) throws IOException {
        var current = account(account);
        var characters = current.characters.stream().map(c -> c.sprite() == null && sprites.containsKey(c.id())
                ? new ClassCharacter(c.id(), c.type(), c.nickname(), sprites.get(c.id())) : c).toList();
        replace(account, new Account(characters, current.shortcuts));
    }

    /** Explicit user reassignment preserves the row's order and preferences; scans never guess it. */
    public void retarget(UUID account, ClassShortcut draft, String targetId) throws IOException {
        var current = account(account);
        if (current.character(targetId) == null) throw new IllegalArgumentException("Character is no longer detected");
        if (!draft.characterId().equals(targetId) && current.shortcuts.stream().anyMatch(s -> s.characterId().equals(targetId)))
            throw new IllegalArgumentException("Character already has a shortcut");
        var shortcuts = new ArrayList<>(current.shortcuts);
        int index = -1;
        for (int i = 0; i < shortcuts.size(); i++) if (shortcuts.get(i).characterId().equals(draft.characterId())) index = i;
        if (index < 0) throw new IllegalArgumentException("Shortcut no longer exists");
        shortcuts.set(index, new ClassShortcut(targetId, draft.name(), draft.binding()));
        replace(account, new Account(current.characters, shortcuts));
    }

    private void replace(UUID id, Account account) throws IOException {
        if (account.equals(accounts.get(id))) return;
        var next = new LinkedHashMap<>(accounts);
        next.put(id, account);
        if (next.size() > 128) throw new IllegalArgumentException("Too many accounts");
        save(next, loads);
    }

    private void save(Map<UUID, Account> next, Map<UUID, Loaded> nextLoads) throws IOException {
        if (!available()) throw new IOException("Class selector storage is unavailable");
        String json = JSON.toJson(new FileData(1, next, nextLoads));
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024 * 1024)
            throw new IllegalArgumentException("Class selector file exceeds 1 MiB");
        var parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, "ralle-classes-", ".tmp");
        try {
            Files.writeString(temporary, json);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        accounts = next;
        loads = nextLoads;
        revision++;
    }
}
