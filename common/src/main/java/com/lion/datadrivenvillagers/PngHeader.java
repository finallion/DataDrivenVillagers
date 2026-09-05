package com.lion.datadrivenvillagers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/// The first 24 bytes of a png: the signature, then the IHDR chunk with width and height. Read
/// before an image is decoded, on both sides: the server so it never sends what no client may
/// decode, the client because a server is not trusted with the native heap. A png of a few hundred
/// kilobytes can unpack to gigabytes, and the decoder allocates before anyone can look.
public final class PngHeader {

    /// Longest side accepted. Vanilla villager textures are 64 wide; a 32x resource pack is 2048.
    public static final int MAX_SIDE = 2048;

    /// Bytes needed for a verdict.
    public static final int LENGTH = 24;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] IHDR = {'I', 'H', 'D', 'R'};

    private PngHeader() {
    }

    /// @param head at least the first {@link #LENGTH} bytes of the file; more is fine
    /// @return why the image must not be decoded, empty when it may
    public static Optional<String> rejection(byte[] head) {
        if (head.length < LENGTH || !Arrays.equals(head, 0, 8, SIGNATURE, 0, 8)) {
            return Optional.of("not a png file");
        }
        if (!Arrays.equals(head, 12, 16, IHDR, 0, 4)) {
            return Optional.of("not a png file (no IHDR chunk)");
        }
        long width = readInt(head, 16);
        long height = readInt(head, 20);
        if (width <= 0 || height <= 0) {
            return Optional.of("png header says " + width + "x" + height + " pixels");
        }
        if (width > MAX_SIDE || height > MAX_SIDE) {
            return Optional.of(width + "x" + height + " pixels, above the " + MAX_SIDE + "x" + MAX_SIDE
                    + " limit");
        }
        return Optional.empty();
    }

    /// Reads only the header, so the size of the file does not matter. The one place that opens a png
    /// for a verdict, so `/ddv why` and the sender cannot drift apart on what they call decodable.
    ///
    /// @return why the image must not be decoded, empty when it may
    public static Optional<String> rejection(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png)) {
            return rejection(in.readNBytes(LENGTH));
        }
    }

    /// Big-endian, unsigned: a width above 2^31 must read as huge, not as negative.
    private static long readInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
