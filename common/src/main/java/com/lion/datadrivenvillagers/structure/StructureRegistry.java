package com.lion.datadrivenvillagers.structure;

import com.lion.datadrivenvillagers.CopyOnWrite;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Loaded structure definitions. Read by the template mixin on every lookup of an id in our namespace,
/// so it must answer without disk access; that lookup runs on world generation's worker threads while
/// `/ddv reload` rewrites the registry on the server thread, hence snapshots swapped whole, see
/// {@link CopyOnWrite}.
public final class StructureRegistry {

    private static volatile Map<Identifier, StructureDefinition> definitions = Collections.emptyMap();
    private static volatile List<LoadError> errors = Collections.emptyList();

    private StructureRegistry() {
    }

    /// @param file   file name the failure came from
    /// @param reason phrased for a pack author
    public record LoadError(String file, String reason) {
    }

    public static void add(StructureDefinition definition) {
        definitions = CopyOnWrite.with(definitions, definition.id(), definition);
    }

    public static void remove(Identifier id) {
        definitions = CopyOnWrite.without(definitions, id);
    }

    public static Optional<StructureDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /// The generated plot behind a template id like `bakery/desert`: definition `bakery`, materials
    /// for `desert`. Empty for an author's nbt, which is served under the definition id itself.
    public static Optional<GeneratedTemplate> generated(Identifier templateId) {
        String path = templateId.getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        Identifier base = Identifier.of(templateId.getNamespace(), path.substring(0, slash));
        StructureDefinition definition = definitions.get(base);
        if (definition == null || !definition.generated()) {
            return Optional.empty();
        }
        return Optional.of(new GeneratedTemplate(definition, path.substring(slash + 1)));
    }

    public record GeneratedTemplate(StructureDefinition definition, String village) {
    }

    public static List<StructureDefinition> ordered() {
        return List.copyOf(definitions.values());
    }

    public static void addError(String file, String reason) {
        errors = CopyOnWrite.plus(errors, new LoadError(file, reason));
    }

    public static void clearErrors() {
        errors = Collections.emptyList();
    }

    public static List<LoadError> errors() {
        return errors;
    }

    public static boolean isEmpty() {
        return definitions.isEmpty();
    }
}
