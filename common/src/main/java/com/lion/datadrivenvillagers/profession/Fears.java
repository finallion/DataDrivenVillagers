package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DefinitionParseException;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// What a profession runs from on sight. `flees_from` adds to vanilla's list, `flees_only_from`
/// replaces it (empty means fears nothing on sight; panic when hurt comes from another sensor and
/// stays). Both in one file is rejected.
///
/// @param entries        the entity types and distances
/// @param replacesVanilla whether vanilla's own list is switched off for this profession
public record Fears(List<EntityRange> entries, boolean replacesVanilla) {

    public static final Fears VANILLA = new Fears(List.of(), false);

    static final String ADD = "flees_from";
    static final String ONLY = "flees_only_from";

    public static Fears parse(JsonObject root) {
        boolean add = root.has(ADD) && !root.get(ADD).isJsonNull();
        boolean only = root.has(ONLY) && !root.get(ONLY).isJsonNull();
        if (add && only) {
            throw new DefinitionParseException("\"" + ADD + "\" adds to vanilla's list and \"" + ONLY
                    + "\" replaces it; a file says one or the other");
        }
        if (only) {
            return new Fears(EntityRange.parse(root, ONLY), true);
        }
        return add ? new Fears(EntityRange.parse(root, ADD), false) : VANILLA;
    }

    /// Whether the file sets either field.
    public boolean isSet() {
        return replacesVanilla || !entries.isEmpty();
    }

    public Optional<EntityRange> of(Identifier entityType) {
        for (EntityRange fear : entries) {
            if (fear.entity().equals(entityType)) {
                return Optional.of(fear);
            }
        }
        return Optional.empty();
    }

    public String describe() {
        if (replacesVanilla) {
            return entries.isEmpty() ? "fears nothing on sight, vanilla's list switched off"
                    : entries.size() + " instead of vanilla's list";
        }
        return entries.size() + " more than vanilla's list";
    }
}
