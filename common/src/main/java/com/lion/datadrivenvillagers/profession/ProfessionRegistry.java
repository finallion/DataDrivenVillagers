package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.CopyOnWrite;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What the loader produced, for the client (texture, hat, name), the `/ddv` commands and reloads.
/// Written during mod init and reloads only, read from the render thread as well in single player:
/// every field is an unmodifiable snapshot swapped whole, see {@link CopyOnWrite}.
public final class ProfessionRegistry {

    private static volatile Map<Identifier, ProfessionDefinition> definitions = Collections.emptyMap();
    private static volatile Map<Identifier, RegistryEntry<PointOfInterestType>> poiEntries = Collections.emptyMap();
    private static volatile List<LoadError> errors = Collections.emptyList();

    private ProfessionRegistry() {
    }

    /// @param file   file name with extension
    /// @param reason phrased for a pack author
    public record LoadError(String file, String reason) {
    }

    /// Keyed by {@link ProfessionDefinition#target()}, so an override of `minecraft:farmer` is found
    /// under the farmer's id.
    ///
    /// @param poi the job site, or null for an override of a profession without one
    public static void add(ProfessionDefinition definition, RegistryEntry<PointOfInterestType> poi) {
        definitions = CopyOnWrite.with(definitions, definition.target(), definition);

        // Only our own job sites; an overridden profession's is already in the tag.
        if (poi != null && !definition.isOverride()) {
            poiEntries = CopyOnWrite.with(poiEntries, definition.target(), poi);
        }
    }

    /// Swaps the definition, keeps the job site. Lookups follow at once; frozen vanilla records do not.
    public static void replace(ProfessionDefinition definition) {
        definitions = CopyOnWrite.with(definitions, definition.target(), definition);
    }

    /// The profession itself stays registered until a restart.
    public static void remove(Identifier target) {
        definitions = CopyOnWrite.without(definitions, target);
        poiEntries = CopyOnWrite.without(poiEntries, target);
    }

    public static void addError(String file, String reason) {
        errors = CopyOnWrite.plus(errors, new LoadError(file, reason));
    }

    /// Called at the start of a reload.
    public static void clearErrors() {
        errors = Collections.emptyList();
    }

    public static Optional<ProfessionDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /// Unmodifiable, in file order.
    public static Map<Identifier, ProfessionDefinition> definitions() {
        return definitions;
    }

    public static List<ProfessionDefinition> ordered() {
        return List.copyOf(definitions.values());
    }

    /// For the tag hook, which puts these into `acquirable_job_site` after every datapack load; the
    /// job site sensor only looks at tagged types.
    public static List<RegistryEntry<PointOfInterestType>> poiEntries() {
        return List.copyOf(poiEntries.values());
    }

    /// Called on the villager tick path; `poiEntries` holds the entries the loader registered.
    public static boolean isOurs(RegistryEntry<PointOfInterestType> poi) {
        return poiEntries.containsValue(poi);
    }

    /// The job site this mod created for a profession; absent for an override.
    public static Optional<RegistryEntry<PointOfInterestType>> poiEntry(Identifier target) {
        return Optional.ofNullable(poiEntries.get(target));
    }

    public static List<LoadError> errors() {
        return errors;
    }

    public static boolean isEmpty() {
        return definitions.isEmpty();
    }
}
