package com.lion.datadrivenvillagers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Every loader's first phase: list the `.json` files in one folder, sorted by name, and hand each to
/// the caller's own parser. Listing failures are logged with the caller's wording; parsing and rejection
/// stay the caller's own.
public final class JsonFolder {

    private static final String EXTENSION = ".json";

    private JsonFolder() {
    }

    /// `noun` names what stays unloaded in the log line, plural, for example "professions".
    public static void forEachFile(Path dir, String noun, Consumer<Path> parseOne) {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {}, no {} will be loaded", dir, noun, e);
            return;
        }
        files.forEach(parseOne);
    }
}
