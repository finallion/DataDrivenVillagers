package com.lion.datadrivenvillagers.type;

import com.lion.datadrivenvillagers.CopyOnWrite;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What the type loader produced. Read by the client for textures, by `/ddv` for reporting, and by
/// the tag hook after every datapack load. Snapshots swapped whole, see {@link CopyOnWrite}.
public final class TypeRegistry {

    private static volatile Map<Identifier, TypeDefinition> definitions = Collections.emptyMap();
    private static volatile List<LoadError> errors = Collections.emptyList();

    private TypeRegistry() {
    }

    /// @param file   file name the failure came from
    /// @param reason phrased for a pack author
    public record LoadError(String file, String reason) {
    }

    public static void add(TypeDefinition definition) {
        definitions = CopyOnWrite.with(definitions, definition.id(), definition);
    }

    /// Biome claims live in `VillagerType.BIOME_TO_TYPE`, rewritten by the loader on reload.
    public static void replace(TypeDefinition definition) {
        definitions = CopyOnWrite.with(definitions, definition.id(), definition);
    }

    /// The registry entry stays until a restart; with no biome pointing at it, no villager gets the type.
    public static void remove(Identifier id) {
        definitions = CopyOnWrite.without(definitions, id);
    }

    public static void addError(String file, String reason) {
        errors = CopyOnWrite.plus(errors, new LoadError(file, reason));
    }

    /// Emptied at the start of a reload.
    public static void clearErrors() {
        errors = Collections.emptyList();
    }

    public static Optional<TypeDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static List<TypeDefinition> ordered() {
        return List.copyOf(definitions.values());
    }

    /// Definitions that claim at least one biome tag, walked by the tag hook on each load.
    public static List<TypeDefinition> withBiomeTags() {
        return definitions.values().stream().filter(d -> !d.biomeTags().isEmpty()).toList();
    }

    public static List<LoadError> errors() {
        return errors;
    }

    public static boolean isEmpty() {
        return definitions.isEmpty();
    }
}
