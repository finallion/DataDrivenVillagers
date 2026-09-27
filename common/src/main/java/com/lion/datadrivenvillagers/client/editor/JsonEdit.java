package com.lion.datadrivenvillagers.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Reads a field out of the author's `JsonObject` as one line of text and writes it back. Only
/// keys with a widget are touched, so `_comment` and unknown fields survive a round trip. A blank
/// value removes the key; the one field where an empty list has meaning (`flees_only_from`) is
/// written by its own button.
public final class JsonEdit {

    /// List separator in a text box. The space is optional on input.
    private static final String SEPARATOR = ", ";

    /// Between an entity id and its distance. A colon is already taken by the id.
    private static final char RANGE = '@';

    private JsonEdit() {
    }

    public static String text(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? "" : safeString(element);
    }

    public static void setText(JsonObject root, String key, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            root.remove(key);
        } else {
            root.addProperty(key, trimmed);
        }
    }

    public static String number(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (!element.isJsonPrimitive()) {
            return safeString(element);
        }
        try {
            double value = element.getAsDouble();
            // Whole numbers read back as 3, not 3.0, so open-and-save does not rewrite the file.
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        } catch (NumberFormatException e) {
            return element.getAsString();
        }
    }

    public static void setNumber(JsonObject root, String key, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            root.remove(key);
            return;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            root.add(key, new JsonPrimitive(parsed == Math.rint(parsed) ? (Number) (long) parsed : parsed));
        } catch (NumberFormatException e) {
            // Written as typed; the server's parser produces the error message.
            root.addProperty(key, trimmed);
        }
    }

    /// Accepts a bare string or an array, like the parser.
    public static String list(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (!element.isJsonArray()) {
            return safeString(element);
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            parts.add(safeString(entry));
        }
        return String.join(SEPARATOR, parts);
    }

    /// A single value is written as a bare string, as hand-written files have it.
    public static void setList(JsonObject root, String key, String value) {
        List<String> parts = split(value);
        if (parts.isEmpty()) {
            root.remove(key);
        } else if (parts.size() == 1) {
            root.addProperty(key, parts.get(0));
        } else {
            JsonArray array = new JsonArray();
            parts.forEach(array::add);
            root.add(key, array);
        }
    }

    /// `minecraft:zombie@10, minecraft:skeleton`: the two shapes `EntityRange` parses.
    public static String ranges(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return element == null || element.isJsonNull() ? "" : safeString(element);
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                String entity = text(object, "entity");
                String distance = number(object, "distance");
                parts.add(distance.isEmpty() ? entity : entity + RANGE + distance);
            } else {
                parts.add(safeString(entry));
            }
        }
        return String.join(SEPARATOR, parts);
    }

    /// Always an array. Blank removes the key; `flees_only_from`'s button writes `[]` itself.
    public static void setRanges(JsonObject root, String key, String value) {
        if (value.trim().isEmpty()) {
            root.remove(key);
            return;
        }
        JsonArray array = new JsonArray();
        for (String part : split(value)) {
            int at = part.indexOf(RANGE);
            if (at < 0) {
                array.add(part);
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("entity", part.substring(0, at).trim());
            setNumber(entry, "distance", part.substring(at + 1));
            array.add(entry);
        }
        root.add(key, array);
    }

    /// `2000=work, 9000=meet`.
    public static String schedule(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            parts.add(number(object, "time") + "=" + text(object, "activity"));
        }
        return String.join(SEPARATOR, parts);
    }

    public static void setSchedule(JsonObject root, String key, String value) {
        JsonArray array = new JsonArray();
        for (String part : split(value)) {
            int equals = part.indexOf('=');
            JsonObject entry = new JsonObject();
            if (equals < 0) {
                // Written as typed; the server's parser produces the error message.
                entry.addProperty("activity", part);
            } else {
                setNumber(entry, "time", part.substring(0, equals));
                entry.addProperty("activity", part.substring(equals + 1).trim().toLowerCase(Locale.ROOT));
            }
            array.add(entry);
        }
        root.add(key, array);
    }

    public static boolean has(JsonObject root, String key) {
        return root.has(key) && !root.get(key).isJsonNull();
    }

    /// A missing key or a value of another type gives a new, detached object.
    public static JsonObject object(JsonObject root, String key) {
        return root.get(key) instanceof JsonObject object ? object : new JsonObject();
    }

    /// A value the box has no shape for is shown as its raw json instead of throwing.
    private static String safeString(JsonElement element) {
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    /// Key present with an empty array: for `flees_only_from` that means "fears nothing".
    public static boolean isEmptyArray(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() && element.getAsJsonArray().isEmpty();
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<>();
        for (String raw : value.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }
}
