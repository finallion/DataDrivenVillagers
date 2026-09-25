package com.lion.datadrivenvillagers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/// Files a definition names beside itself, and files this mod writes into the config folder.
///
/// A name out of a json file is not trusted: the editor lets an operator write one over the
/// network, and on Windows a backslash inside a "file name" is a path separator, so a name of the
/// shape `..` backslash `..` backslash `x` leaves the folder. Two locks: the parser holds every name
/// to {@link #FILE_NAME}, and every place that turns a name into a path checks that the result still
/// lies in the folder it started from.
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /// `\` is blocked on every platform, not only where it separates, so packs behave the same everywhere.
    public static boolean isFileName(String raw) {
        if (raw.isEmpty() || raw.equals(".") || raw.equals("..")) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '\0') {
                return false;
            }
        }
        return true;
    }

    /// @throws DefinitionParseException naming the field, when the value is not a plain file name
    public static String requireFileName(String raw, String field) {
        if (!isFileName(raw)) {
            throw new DefinitionParseException("\"" + field + "\" must be the name of a file sitting next "
                    + "to the json, not a path: no / or \\ or :, got \"" + raw + "\"");
        }
        return raw;
    }

    /// The file `name` names inside `folder`, or empty when the name would lead anywhere else.
    public static Optional<Path> resolveInside(Path folder, String name) {
        Path base = folder.toAbsolutePath().normalize();
        Path file;
        try {
            file = base.resolve(name).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        return base.equals(file.getParent()) ? Optional.of(file) : Optional.empty();
    }

    /// Writes a sibling `.tmp` and moves it over; loaders ignore `.tmp`, so a crash keeps the old file.
    public static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
