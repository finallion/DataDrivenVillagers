package com.lion.datadrivenvillagers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    /// Odd but harmless names an author may have; the rule refuses paths, not spellings.
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

    /// Also checks no stray `.tmp` file remains; the loaders would report one as a rejected definition.
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
    void writesThroughAStreamAndCleansUpOnFailure(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("village.nbt");

        ConfigFiles.writeAtomically(file, stream -> stream.write(new byte[] {1, 2, 3}));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(file));

        assertThrows(IllegalStateException.class, () -> ConfigFiles.writeAtomically(file, stream -> {
            throw new IllegalStateException("boom");
        }));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(file));

        try (Stream<Path> left = Files.list(folder)) {
            assertEquals(List.of("village.nbt"), left.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    /// A collision on the random temp name is retried; one thrown by the writer itself names another file.
    @Test
    void aFileAlreadyExistsExceptionFromTheWriterIsNotANameCollision(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("village.nbt");

        assertThrows(FileAlreadyExistsException.class, () -> ConfigFiles.writeAtomically(file, stream -> {
            throw new FileAlreadyExistsException("somewhere/else.tmp");
        }));

        try (Stream<Path> left = Files.list(folder)) {
            assertTrue(left.findAny().isEmpty(), "the temp file must not be left behind");
        }
    }

    @Test
    void resolveRefusesEverythingThatLeaves() {
        assertTrue(ConfigFiles.resolveInside(FOLDER, "../x.png").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "..").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "").isEmpty());
        assertTrue(ConfigFiles.resolveInside(FOLDER, "sub/x.png").isEmpty());
        // On Windows this walks up two folders; on Linux it is one odd file name inside the folder.
        ConfigFiles.resolveInside(FOLDER, ".." + BS + ".." + BS + "x.png");
    }

    /// `"..."` is not caught by `isFileName`, since it stays inside the folder lexically.
    @Test
    void onlyDotNamesAreCaught() {
        assertTrue(ConfigFiles.isOnlyDots("."));
        assertTrue(ConfigFiles.isOnlyDots(".."));
        assertTrue(ConfigFiles.isOnlyDots("...."));
        assertFalse(ConfigFiles.isOnlyDots(""));
        assertFalse(ConfigFiles.isOnlyDots("a.."));
        assertFalse(ConfigFiles.isOnlyDots("baker.png"));
    }

    @Test
    void windowsDeviceNamesAreCaughtWhateverFollowsTheDot() {
        assertTrue(ConfigFiles.isWindowsDeviceName("con"));
        assertTrue(ConfigFiles.isWindowsDeviceName("CON"));
        assertTrue(ConfigFiles.isWindowsDeviceName("com1.json"));
        assertTrue(ConfigFiles.isWindowsDeviceName("lpt9.json.tmp"));
        assertFalse(ConfigFiles.isWindowsDeviceName("console"));
        assertFalse(ConfigFiles.isWindowsDeviceName("com10"));
        assertFalse(ConfigFiles.isWindowsDeviceName("baker.png"));
    }

    /// A datapack or zip entry path, not a file system path: `/` is always the separator, never `\`.
    @Test
    void unsafeSegmentsAreCaughtInADatapackOrZipPath() {
        assertTrue(ConfigFiles.hasUnsafeSegment("datapack/data/../loot_table/evil.json"));
        assertTrue(ConfigFiles.hasUnsafeSegment("datapack/data/./loot_table/evil.json"));
        assertTrue(ConfigFiles.hasUnsafeSegment("datapack/data//loot_table/evil.json"));
        assertTrue(ConfigFiles.hasUnsafeSegment("../evil.json"));
        assertTrue(ConfigFiles.hasUnsafeSegment("evil.json/.."));
        assertFalse(ConfigFiles.hasUnsafeSegment("datapack/data/datadrivenvillagers/loot_table/baker.json"));
        assertFalse(ConfigFiles.hasUnsafeSegment("a..b/x.json"));
    }
}
