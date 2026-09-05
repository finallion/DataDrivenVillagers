package com.lion.datadrivenvillagers.structure;

import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Turns one json file into a {@link StructureDefinition}. No registry access, so it is unit testable
/// without booting Minecraft.
public final class StructureParser {

    /// Every vanilla village type. A file that names none means all of them.
    public static final List<String> ALL_VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    /// Pools a piece may go into. `terminators` and `villagers` are machinery, not places for a building.
    public static final List<String> POOLS = List.of("houses", "decor", "streets");

    private static final String DEFAULT_POOL = "houses";
    private static final int DEFAULT_WEIGHT = 1;
    private static final String EXTENSION = ".nbt";

    private StructureParser() {
    }

    /// @param fileName file name without extension, becomes the registry path
    public static StructureDefinition parse(String fileName, JsonObject root) {
        String path = JsonFields.idPathFromFileName(fileName);

        // Exactly one of "structure" and "workstation" per file.
        Optional<String> file = JsonFields.optionalString(root, "structure");
        Optional<Identifier> workstation =
                JsonFields.optionalString(root, "workstation").map(JsonFields::identifier);
        if (file.isPresent() && workstation.isPresent()) {
            throw new DefinitionParseException("\"structure\" names a saved building and \"workstation\" "
                    + "asks for a generated one; a file says one or the other");
        }
        if (file.isEmpty() && workstation.isEmpty()) {
            throw new DefinitionParseException("\"structure\" must name the nbt file beside this json, or "
                    + "\"workstation\" a block to build a plot around");
        }
        if (file.isPresent() && !file.get().endsWith(EXTENSION)) {
            // Rejected rather than fixed: a name without the extension is probably a datapack id.
            throw new DefinitionParseException("\"structure\" must be a file name ending in " + EXTENSION
                    + ", the nbt sits beside the json rather than in a datapack");
        }
        // Same rule as a texture: a plain file name beside the json, never a path out of the folder.
        file.ifPresent(name -> ConfigFiles.requireFileName(name, "structure"));

        int weight = JsonFields.positiveInt(root, "weight", DEFAULT_WEIGHT);

        List<Identifier> pools = JsonFields.identifiers(root, "pools", false);
        List<String> villages = JsonFields.strings(root, "villages");
        String pool = JsonFields.optionalString(root, "pool").orElse(DEFAULT_POOL);

        // "pools" and the "villages"/"pool" shorthand are exclusive.
        if (!pools.isEmpty() && (!villages.isEmpty() || root.has("pool"))) {
            throw new DefinitionParseException("\"pools\" names the pools outright and cannot be used "
                    + "with \"villages\" or \"pool\", which are the shorthand for the same thing");
        }

        for (String village : villages) {
            if (!ALL_VILLAGES.contains(village.toLowerCase(Locale.ROOT))) {
                throw new DefinitionParseException("unknown village \"" + village + "\", expected one of "
                        + ALL_VILLAGES);
            }
        }
        if (!POOLS.contains(pool.toLowerCase(Locale.ROOT))) {
            throw new DefinitionParseException("unknown pool \"" + pool + "\", expected one of " + POOLS
                    + ", or name the pools outright with \"pools\"");
        }

        GroundKind ground = JsonFields.optionalString(root, "ground")
                .map(GroundKind::parse)
                .orElse(GroundKind.RIGID);
        Optional<Identifier> processors =
                JsonFields.optionalString(root, "processors").map(JsonFields::identifier);

        return new StructureDefinition(
                DataDrivenVillagers.id(path),
                file,
                workstation,
                weight,
                villages.isEmpty() ? ALL_VILLAGES : List.copyOf(villages),
                pool.toLowerCase(Locale.ROOT),
                pools,
                ground,
                processors);
    }
}
