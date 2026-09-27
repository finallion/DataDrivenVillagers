package com.lion.datadrivenvillagers;

import java.nio.file.FileSystemException;
import java.nio.file.Path;

/// A rejected definition file, of any kind. The message is for the pack author: it names the field and
/// what was expected. Loaders catch it; a broken file must not stop the game.
public class DefinitionParseException extends RuntimeException {

    private static final int MAX_LENGTH = 500;

    public DefinitionParseException(String message) {
        super(message);
    }

    public DefinitionParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Strips the Gson class prefix from its messages, or shows a file system exception's path relative.
    public static String readableReason(Exception e) {
        if (e instanceof FileSystemException fse && fse.getFile() != null) {
            String reason = fse.getReason() == null ? "" : ": " + fse.getReason();
            return sanitize(e.getClass().getSimpleName() + " on " + ConfigFiles.relative(Path.of(fse.getFile())) + reason);
        }
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        String stripped = message.replaceFirst("^(?:[a-z][a-z0-9]*\\.)+[A-Za-z0-9_$]*(?:Exception|Error):\\s*", "");
        return sanitize(stripped);
    }

    /// Removes control characters and the section sign, so the text cannot fake a log line or a colour code.
    public static String sanitize(String text) {
        int limit = Math.min(text.length(), MAX_LENGTH);
        StringBuilder out = new StringBuilder(limit);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            out.append(isUnsafe(c) ? ' ' : c);
        }
        return limit < text.length() ? out + "..." : out.toString();
    }

    /// Covers C0 and C1 controls plus the two line separators a log viewer or a text widget also breaks on.
    private static boolean isUnsafe(char c) {
        return Character.isISOControl(c) || c == '\u00a7' || c == '\u2028' || c == '\u2029';
    }
}
