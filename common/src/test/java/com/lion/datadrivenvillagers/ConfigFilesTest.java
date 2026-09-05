package com.lion.datadrivenvillagers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The two locks against a texture or structure name that leaves the folder. The backslash cases are
/// the reason this exists: on Windows `Path.resolve` treats it as a separator, on Linux it is a
/// letter, and the rule has to reject it on both.
class ConfigFilesTest {

    private static final String BS = String.valueOf((char) 92);

    private static final Path FOLDER = Path.of("config", "datadrivenvillagers", "professions");

    @Test
    void plainNamesPass() {
        assertTrue(ConfigFiles.isFileName("baker.png"));
        assertTrue(ConfigFiles.isFileName("Baker.PNG"));
        assertTrue(ConfigFiles.isFileName("my_baker-2.v1.png"));
        assertTrue(ConfigFiles.isFileName("bakery.nbt"));
        assertTrue(ConfigFiles.isFileName("noextension"));
    }

    /// Odd but harmless names an author may already have. The rule refuses paths, not spellings, so
    /// none of these may start failing.
    @Test
    void oddButHarmlessNamesStillPass() {
        assertTrue(ConfigFiles.isFileName("my baker.png"));
        assertTrue(ConfigFiles.isFileName(".hidden.png"));
        assertTrue(ConfigFiles.isFileName("a..b.png"));
        assertTrue(ConfigFiles.isFileName("trailing."));
        assertTrue(ConfigFiles.isFileName("bäcker.png"));
        assertTrue(ConfigFiles.isFileName("..png"));
    }

    @Test
    void separatorsAndDotNamesAreRejected() {
        assertFalse(ConfigFiles.isFileName(".." + BS + ".." + BS + "server.properties"));
        assertFalse(ConfigFiles.isFileName("sub" + BS + "x.png"));
        assertFalse(ConfigFiles.isFileName("../x.png"));
        assertFalse(ConfigFiles.isFileName("sub/x.png"));
        assertFalse(ConfigFiles.isFileName(".."));
        assertFalse(ConfigFiles.isFileName("."));
        assertFalse(ConfigFiles.isFileName("c:x.png"));
        assertFalse(ConfigFiles.isFileName("x.png:stream"));
        assertFalse(ConfigFiles.isFileName(""));
    }

    @Test
    void requireNamesTheField() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class,
                () -> ConfigFiles.requireFileName(".." + BS + "x.png", "texture"));
        assertTrue(e.getMessage().startsWith("\"texture\""));
        assertEquals("baker.png", ConfigFiles.requireFileName("baker.png", "texture"));
    }

    @Test
    void resolveStaysInsideTheFolder() {
        Path inside = ConfigFiles.resolveInside(FOLDER, "baker.png").orElseThrow();
        assertEquals(FOLDER.toAbsolutePath().normalize(), inside.getParent());
        assertEquals("baker.png", inside.getFileName().toString());
    }

    /// The profession file is somebody's work, so a save must not be able to halve it. Also covers
    /// that the temporary file is gone afterwards: the loaders would report a stray one as rejected.
    @Test
    void writesThroughATemporaryFileAndLeavesNoneBehind(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("baker.json");

        ConfigFiles.writeAtomically(file, "{ \"workstation\": \"minecraft:campfire\" }");
        assertEquals("{ \"workstation\": \"minecraft:campfire\" }",
                Files.readString(file, StandardCharsets.UTF_8));

        ConfigFiles.writeAtomically(file, "{ \"workstation\": \"minecraft:anvil\" }");
        assertEquals("{ \"workstation\": \"minecraft:anvil\" }",
                Files.readString(file, StandardCharsets.UTF_8));

        try (Stream<Path> left = Files.list(folder)) {
            assertEquals(List.of("baker.json"), left.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void resolveRefusesEverythingThatLeaves() {
        assertTrue(ConfigFiles.resolveInside(FOLDER, "../x.png").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "..").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "sub/x.png").isEmpty());
        // On Windows this walks up two folders; on Linux it is one odd file name inside the folder.
        // Either way the parser has already refused it, so this second lock only has to not throw.
        ConfigFiles.resolveInside(FOLDER, ".." + BS + ".." + BS + "x.png");
    }
}
