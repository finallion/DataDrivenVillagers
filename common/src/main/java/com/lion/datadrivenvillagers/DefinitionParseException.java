package com.lion.datadrivenvillagers;

/// A rejected definition file, of any kind. The message is for the pack author: it names the field and
/// what was expected. Loaders catch it; a broken file must not stop the game.
public class DefinitionParseException extends RuntimeException {

    public DefinitionParseException(String message) {
        super(message);
    }

    public DefinitionParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Strips the exception class name Gson prefixes to its messages; line and column stay.
    public static String readableReason(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.replaceFirst("^(?:[a-z][a-z0-9]*\\.)+[A-Za-z0-9_$]*(?:Exception|Error):\\s*", "");
    }
}
