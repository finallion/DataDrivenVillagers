package com.lion.datadrivenvillagers;

import com.google.gson.JsonElement;

import java.util.Map;

/// How far a json tree of objects and arrays nests. Checked wherever a tree might later be written
/// back out with Gson: on the client before the editor opens one sent by the server, and on the
/// server before it saves one sent by the editor. A tree deep enough only exists to overflow that
/// write's stack.
public final class JsonDepth {

    /// Deeper than a hand written file would ever need; past this a value exists only to crash a Save button.
    public static final int MAX_DEPTH = 64;

    private JsonDepth() {
    }

    /// The recursion itself never goes past `maxDepth`, whatever depth the json claims to have.
    public static boolean exceeds(JsonElement element, int maxDepth) {
        return exceeds(element, 0, maxDepth);
    }

    private static boolean exceeds(JsonElement element, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return true;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (exceeds(entry.getValue(), depth + 1, maxDepth)) {
                    return true;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (exceeds(child, depth + 1, maxDepth)) {
                    return true;
                }
            }
        }
        return false;
    }
}
