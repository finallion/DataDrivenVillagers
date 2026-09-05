package com.lion.datadrivenvillagers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Reads fields out of a definition file. Every error message names the field: it ends up in
/// `/ddv errors`.
public final class JsonFields {

    private JsonFields() {
    }

    /// Accepts a single string or an array of strings.
    public static List<Identifier> identifiers(JsonObject root, String field, boolean required) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            if (required) {
                throw new DefinitionParseException("missing required field \"" + field + "\"");
            }
            return List.of();
        }

        List<Identifier> result = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                result.add(identifier(asString(array.get(i), field + "[" + i + "]")));
            }
        } else {
            result.add(identifier(asString(element, field)));
        }
        return List.copyOf(result);
    }

    /// Same as {@link #identifiers}, unparsed: the caller tells a `#tag` from a plain id.
    public static List<String> strings(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                result.add(asString(array.get(i), field + "[" + i + "]"));
            }
        } else {
            result.add(asString(element, field));
        }
        return List.copyOf(result);
    }

    /// Where a definition's image comes from. Exactly one of the two is present, or neither.
    ///
    /// @param identifier a resource pack identifier
    /// @param file       a png sitting next to the json
    public record TextureSource(Optional<Identifier> identifier, Optional<String> file) {

        static final TextureSource NONE = new TextureSource(Optional.empty(), Optional.empty());
    }

    /// A bare file name is a png next to the json; a value with a colon or slash is a resource pack
    /// identifier.
    ///
    /// The file name is held to {@link ConfigFiles#isFileName}: it is read from disk and sent to
    /// every player, an operator can write one over the network through the editor, and on Windows a
    /// backslash inside it is a path separator.
    public static TextureSource texture(JsonObject root, String field) {
        Optional<String> raw = optionalString(root, field);
        if (raw.isEmpty()) {
            return TextureSource.NONE;
        }

        String value = raw.get();
        if (value.contains(":") || value.contains("/")) {
            return new TextureSource(Optional.of(identifier(value)), Optional.empty());
        }
        return new TextureSource(Optional.empty(),
                Optional.of(ConfigFiles.requireFileName(value, field)));
    }

    public static Identifier identifier(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            throw new DefinitionParseException("\"" + raw + "\" is not a valid identifier");
        }
        return id;
    }

    public static Optional<String> optionalString(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(asString(element, field));
    }

    public static String asString(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new DefinitionParseException("\"" + field + "\" must be a string");
        }
        return element.getAsString();
    }

    /// Empty when the field is absent (keep vanilla's value).
    public static Optional<Double> positiveDouble(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionParseException("\"" + field + "\" must be a number");
        }
        double value = element.getAsDouble();
        if (value <= 0) {
            throw new DefinitionParseException("\"" + field + "\" must be above 0, got " + value);
        }
        return Optional.of(value);
    }

    public static int positiveInt(JsonObject root, String field, int fallback) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionParseException("\"" + field + "\" must be a number");
        }
        int value = element.getAsInt();
        if (value < 1) {
            throw new DefinitionParseException("\"" + field + "\" must be at least 1, got " + value);
        }
        return value;
    }

    /// The file name becomes the registry path, so it is checked against the id path charset here.
    public static String idPathFromFileName(String fileName) {
        String path = fileName.toLowerCase(java.util.Locale.ROOT);
        if (!path.matches("[a-z0-9_.-]+")) {
            throw new DefinitionParseException(
                    "file name \"" + fileName + "\" is not a valid id, use lower case letters, digits and _");
        }
        return path;
    }
}
