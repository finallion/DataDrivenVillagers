package com.lion.datadrivenvillagers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PngHeaderTest {

    private static byte[] header(long width, long height) {
        byte[] head = new byte[PngHeader.LENGTH];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, head, 0, 8);
        head[11] = 13;
        head[12] = 'I';
        head[13] = 'H';
        head[14] = 'D';
        head[15] = 'R';
        putInt(head, 16, width);
        putInt(head, 20, height);
        return head;
    }

    private static void putInt(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    @Test
    void aVillagerTexturePasses() {
        assertEquals(Optional.empty(), PngHeader.rejection(header(64, 64)));
        assertEquals(Optional.empty(), PngHeader.rejection(header(PngHeader.MAX_SIDE, PngHeader.MAX_SIDE)));
    }

    /// The example png shipped in the jar, read the way the loader reads an author's file.
    @Test
    void theShippedExamplePasses() throws IOException {
        try (InputStream in = PngHeaderTest.class.getResourceAsStream(
                "/assets/datadrivenvillagers/example/example_baker.png")) {
            assertTrue(in != null, "example png is in the jar");
            assertEquals(Optional.empty(), PngHeader.rejection(in.readNBytes(PngHeader.LENGTH)));
        }
    }

    /// The overload the sender and `/ddv why` both call. It must reach a verdict from the header
    /// alone, or a huge file would be read into memory to find out that it may not be decoded.
    @Test
    void aFileIsJudgedFromItsHeaderAlone(@TempDir Path folder) throws IOException {
        Path bomb = folder.resolve("bomb.png");
        byte[] head = header(16384, 16384);
        Files.write(bomb, head);
        assertTrue(PngHeader.rejection(bomb).orElseThrow().contains("16384x16384"));

        Path fine = folder.resolve("fine.png");
        // Header only, no image data: the verdict must not depend on the rest of the file.
        Files.write(fine, header(64, 64));
        assertEquals(Optional.empty(), PngHeader.rejection(fine));

        Path truncated = folder.resolve("truncated.png");
        Files.write(truncated, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        assertEquals("not a png file", PngHeader.rejection(truncated).orElseThrow());
    }

    @Test
    void aBombIsRefusedBeforeDecoding() {
        assertTrue(PngHeader.rejection(header(16384, 16384)).orElseThrow().contains("16384x16384"));
        assertTrue(PngHeader.rejection(header(PngHeader.MAX_SIDE + 1, 1)).isPresent());
        // 0xFFFFFFFF reads as unsigned, not as -1.
        assertTrue(PngHeader.rejection(header(0xFFFFFFFFL, 1)).isPresent());
        assertTrue(PngHeader.rejection(header(0, 64)).isPresent());
    }

    @Test
    void somethingElseThanPngIsRefused() {
        byte[] jpeg = header(64, 64);
        jpeg[0] = (byte) 0xFF;
        assertEquals("not a png file", PngHeader.rejection(jpeg).orElseThrow());

        byte[] noIhdr = header(64, 64);
        noIhdr[12] = 'X';
        assertTrue(PngHeader.rejection(noIhdr).orElseThrow().contains("IHDR"));

        assertEquals("not a png file", PngHeader.rejection(new byte[8]).orElseThrow());
        assertEquals("not a png file", PngHeader.rejection(new byte[0]).orElseThrow());
    }
}
