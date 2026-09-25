package com.lion.datadrivenvillagers.client.editor;

/// A comma separated list in a text box, and which of its values the cursor stands in.
final class ListParts {

    private ListParts() {
    }

    /// The value the cursor stands in, trimmed: from the comma before the cursor to the comma after.
    static String at(String text, int cursor) {
        return text.substring(start(text, cursor), end(text, cursor)).trim();
    }

    /// Whether the cursor stands in the last value, the only place vanilla draws a ghost completion.
    static boolean atEnd(String text, int cursor) {
        return text.indexOf(',', clamp(text, cursor)) < 0;
    }

    /// The text with the value at the cursor replaced, the other values and their commas untouched.
    static String replace(String text, int cursor, String replacement) {
        return before(text, cursor, replacement) + text.substring(end(text, cursor));
    }

    /// Where the cursor belongs after {@link #replace}: right after what was put in.
    static int cursorAfterReplace(String text, int cursor, String replacement) {
        return before(text, cursor, replacement).length();
    }

    private static String before(String text, int cursor, String replacement) {
        String kept = text.substring(0, start(text, cursor));
        return kept + (kept.isEmpty() ? "" : " ") + replacement;
    }

    private static int start(String text, int cursor) {
        int comma = text.lastIndexOf(',', clamp(text, cursor) - 1);
        return comma < 0 ? 0 : comma + 1;
    }

    private static int end(String text, int cursor) {
        int comma = text.indexOf(',', clamp(text, cursor));
        return comma < 0 ? text.length() : comma;
    }

    private static int clamp(String text, int cursor) {
        return Math.max(0, Math.min(cursor, text.length()));
    }
}
