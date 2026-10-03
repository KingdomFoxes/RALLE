package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class SettingsRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsRegistry.class);
    private final Map<String, SettingsCategory> categories = new LinkedHashMap<>();
    private final Map<String, List<String>> dependencies = new LinkedHashMap<>();
    private final Map<String, Component> unavailableReasons = new LinkedHashMap<>();
    private final Map<String, RuntimeAccess> runtimeAccess = new LinkedHashMap<>();
    private final Path storagePath;
    private boolean sealed;

    public SettingsRegistry(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
    }

    public void registerCategory(SettingsCategory category) {
        requireOpen();
        Objects.requireNonNull(category, "category");
        if (categories.containsKey(category.id())) {
            throw new IllegalArgumentException("Duplicate settings category id: " + category.id());
        }

        var entryIds = new HashSet<String>();
        entries().stream().map(SettingsEntry::id).forEach(entryIds::add);
        for (var entry : category.entries()) {
            if (!entryIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate settings entry id: " + entry.id());
            }
        }
        var subcategoryIds = new HashSet<String>();
        for (var subcategory : category.subcategories()) {
            if (!subcategoryIds.add(subcategory.id())) {
                throw new IllegalArgumentException(
                        "Duplicate settings subcategory id in " + category.id() + ": " + subcategory.id()
                );
            }
            for (var entry : subcategory.entries()) {
                if (!entryIds.add(entry.id())) {
                    throw new IllegalArgumentException("Duplicate settings entry id: " + entry.id());
                }
            }
        }
        categories.put(category.id(), category);
    }

    /** Declares that an entry is available only while the referenced boolean setting is enabled. */
    public void requireEnabled(String entryId, String requiredBooleanSettingId) {
        requireOpen();
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(requiredBooleanSettingId, "requiredBooleanSettingId");
        dependencies.computeIfAbsent(entryId, ignored -> new ArrayList<>()).add(requiredBooleanSettingId);
    }

    /** Makes an entry visible but non-interactive when an optional runtime integration is unavailable. */
    public void markUnavailable(String entryId, Component reason) {
        requireOpen();
        Objects.requireNonNull(entryId, "entryId");
        unavailableReasons.put(entryId, Objects.requireNonNull(reason, "reason"));
    }

    /** Locks a toggle and masks its runtime value, including loaded defaults, while access is absent. */
    public void requireAccess(String entryId, java.util.function.BooleanSupplier allowed, Component reason) {
        requireOpen();
        var toggle = setting(entryId, BooleanSetting.class);
        var access = new RuntimeAccess(Objects.requireNonNull(allowed, "allowed"),
                Objects.requireNonNull(reason, "reason"));
        if (runtimeAccess.putIfAbsent(entryId, access) != null)
            throw new IllegalArgumentException("Duplicate runtime access requirement: " + entryId);
        toggle.requireAccess(allowed);
    }

    private record RuntimeAccess(java.util.function.BooleanSupplier allowed, Component reason) {}

    public Collection<SettingsCategory> categories() {
        return List.copyOf(categories.values());
    }

    public List<SettingsEntry> entries() {
        return categories.values().stream()
                .flatMap(category -> java.util.stream.Stream.concat(
                        category.entries().stream(),
                        category.subcategories().stream().flatMap(subcategory -> subcategory.entries().stream())
                ))
                .toList();
    }

    public Optional<SettingsEntry> entry(String id) {
        Objects.requireNonNull(id, "id");
        return entries().stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public List<String> dependencies(String entryId) {
        return List.copyOf(dependencies.getOrDefault(entryId, List.of()));
    }

    public List<BooleanSetting> unmetDependencies(String entryId) {
        var unmet = new LinkedHashMap<String, BooleanSetting>();
        collectUnmetDependencies(entryId, unmet);
        return List.copyOf(unmet.values());
    }

    public boolean available(String entryId) {
        return unmetDependencies(entryId).isEmpty() && unavailableReason(entryId).isEmpty();
    }

    /** Dependency-hidden children are omitted; capability-unavailable entries remain visible with an explanation. */
    public boolean visible(String entryId) {
        return unmetDependencies(entryId).isEmpty();
    }

    public Optional<Component> unavailableReason(String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        var access = runtimeAccess.get(entryId);
        if (access != null && !access.allowed().getAsBoolean()) return Optional.of(access.reason());
        return Optional.ofNullable(unavailableReasons.get(entryId));
    }

    private void collectUnmetDependencies(String entryId, Map<String, BooleanSetting> unmet) {
        for (var dependencyId : dependencies(entryId)) {
            var dependency = setting(dependencyId, BooleanSetting.class);
            if (!dependency.value()) unmet.putIfAbsent(dependencyId, dependency);
            collectUnmetDependencies(dependencyId, unmet);
        }
    }

    public <S extends Setting<?>> S setting(String id, Class<S> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        return entries().stream()
                .filter(Setting.class::isInstance)
                .map(entry -> (Setting<?>) entry)
                .filter(setting -> setting.id().equals(id))
                .findFirst()
                .map(setting -> {
                    if (!type.isInstance(setting)) {
                        throw new IllegalArgumentException("Setting " + id + " is not a " + type.getSimpleName());
                    }
                    return type.cast(setting);
                })
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + id));
    }

    public void seal() {
        requireOpen();
        validateDependencies();
        load();
        entries().stream()
                .filter(Setting.class::isInstance)
                .map(entry -> (Setting<?>) entry)
                .forEach(setting -> setting.onChanged(ignored -> save()));
        sealed = true;
        if (!Files.exists(storagePath)) save();
    }

    private void load() {
        if (!Files.exists(storagePath)) return;
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read RALLE settings from {}. Defaults will be used", storagePath, exception);
            return;
        }

        forEachSetting((category, setting) -> {
            var value = properties.getProperty(category.id() + "." + setting.id());
            if (value == null) return;
            try {
                setting.load(value);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring invalid RALLE setting {}.{}={}", category.id(), setting.id(), value);
            }
        });
    }

    private void save() {
        var properties = new Properties();
        forEachSetting((category, setting) ->
                properties.setProperty(category.id() + "." + setting.id(), setting.serialize()));
        try {
            Files.createDirectories(storagePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                properties.store(writer, "RALLE local settings");
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save RALLE settings to {}", storagePath, exception);
        }
    }

    private void forEachSetting(java.util.function.BiConsumer<SettingsCategory, Setting<?>> consumer) {
        for (var category : categories.values()) {
            for (var entry : category.entries()) {
                if (entry instanceof Setting<?> setting) consumer.accept(category, setting);
            }
            for (var subcategory : category.subcategories()) {
                for (var entry : subcategory.entries()) {
                    if (entry instanceof Setting<?> setting) consumer.accept(category, setting);
                }
            }
        }
    }

    private void validateDependencies() {
        var entryIds = entries().stream().map(SettingsEntry::id).collect(java.util.stream.Collectors.toSet());
        for (var entryId : unavailableReasons.keySet()) {
            if (!entryIds.contains(entryId)) {
                throw new IllegalArgumentException("Unknown unavailable settings entry: " + entryId);
            }
        }
        for (var dependency : dependencies.entrySet()) {
            if (!entryIds.contains(dependency.getKey())) {
                throw new IllegalArgumentException("Unknown dependent settings entry: " + dependency.getKey());
            }
            for (var requiredId : dependency.getValue()) {
                if (!entryIds.contains(requiredId)) {
                    throw new IllegalArgumentException("Unknown required setting: " + requiredId);
                }
                setting(requiredId, BooleanSetting.class);
            }
        }
        var visiting = new HashSet<String>();
        var visited = new HashSet<String>();
        for (var id : entryIds) visitDependency(id, visiting, visited, new ArrayDeque<>());
    }

    private void visitDependency(
            String id,
            HashSet<String> visiting,
            HashSet<String> visited,
            ArrayDeque<String> path
    ) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            path.addLast(id);
            throw new IllegalArgumentException("Settings dependency cycle: " + String.join(" -> ", path));
        }
        path.addLast(id);
        for (var dependency : dependencies.getOrDefault(id, List.of())) {
            visitDependency(dependency, visiting, visited, path);
        }
        path.removeLast();
        visiting.remove(id);
        visited.add(id);
    }

    private void requireOpen() {
        if (sealed) throw new IllegalStateException("Settings registration is sealed");
    }
}
