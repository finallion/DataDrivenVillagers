package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/// One entry of `flees_from` or `attacks`: an entity type and the distance at which it counts.
///
/// @param entity   an entity type id. Tags are rejected: the sensor compares types, and resolving a
///                 tag needs a registry the parser does not have
/// @param distance blocks, the radius inside which the entity counts
public record EntityRange(Identifier entity, int distance) {

    /// Vanilla's fear distance for a zombie.
    public static final int DEFAULT_DISTANCE = 8;

    /// A bare id, or an object with `entity` and `distance`; one of either, or a list.
    public static List<EntityRange> parse(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<JsonElement> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(entries::add);
        } else {
            entries.add(element);
        }

        List<EntityRange> fears = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String where = field + "[" + i + "]";
            JsonElement entry = entries.get(i);
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                Identifier entity = JsonFields.optionalString(object, "entity").map(JsonFields::identifier)
                        .orElseThrow(() -> new DefinitionParseException("\"" + where + "\" is missing \"entity\""));
                fears.add(new EntityRange(entity, JsonFields.positiveInt(object, "distance", DEFAULT_DISTANCE)));
            } else {
                String raw = JsonFields.asString(entry, where);
                if (raw.startsWith("#")) {
                    throw new DefinitionParseException("\"" + where + "\" is a tag, but " + field + " takes "
                            + "entity ids: one type is compared at a time");
                }
                fears.add(new EntityRange(JsonFields.identifier(raw), DEFAULT_DISTANCE));
            }
        }
        return List.copyOf(fears);
    }

    public String describe() {
        return entity + " within " + distance;
    }
}
