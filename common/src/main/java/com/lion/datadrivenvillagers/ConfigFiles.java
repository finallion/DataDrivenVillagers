package com.lion.datadrivenvillagers;

import com.lion.datadrivenvillagers.platform.ConfigDirectory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Files a definition names beside itself, and files this mod writes into the config folder.
///
/// A name out of a json file is not trusted: the editor lets an operator write one over the
/// network, and on Windows a backslash inside a "file name" is a path separator, so a name of the
/// shape `..` backslash `..` backslash `x` leaves the folder. Two locks: the parser holds every name
/// to {@link #FILE_NAME}, and every place that turns a name into a path checks that the result still
/// lies in the folder it started from.
public final class ConfigFiles {

    /// Windows redirects these to a device, regardless of case or of anything after the first dot.
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    /// Attempts to claim a random temp file name before giving up and reporting the last failure.
    private static final int TEMP_NAME_ATTEMPTS = 8;

    private ConfigFiles() {
    }

    /// The config directory's parent.
    private static Path gameDirectory() {
        return ConfigDirectory.getConfigDirectory().getParent().toAbsolutePath().normalize();
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

    /// `"..."` is not `".."` and stays inside a folder, but some tools still treat it as a parent reference.
    public static boolean isOnlyDots(String raw) {
        return !raw.isEmpty() && raw.chars().allMatch(c -> c == '.');
    }

    /// True for a `/`-joined datapack or zip entry path with an empty, "." or ".." segment, its own way out.
    public static boolean hasUnsafeSegment(String slashSeparatedPath) {
        for (String segment : slashSeparatedPath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    /// Windows treats `com1.json` as the device `com1`: only the part before the first dot counts.
    public static boolean isWindowsDeviceName(String raw) {
        int dot = raw.indexOf('.');
        String base = dot < 0 ? raw : raw.substring(0, dot);
        return WINDOWS_DEVICE_NAMES.contains(base.toLowerCase(Locale.ROOT));
    }

    /// Shown to a player instead of the full disk path, which can carry an OS user name.
    public static String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return gameDirectory().relativize(absolute).toString();
        } catch (IllegalArgumentException e) {
            // Different roots, on Windows a different drive letter.
            return absolute.getFileName().toString();
        }
    }

    /// @throws DefinitionParseException naming the field, when the value is not a plain file name
    public static String requireFileName(String raw, String field) {
        if (!isFileName(raw)) {
            throw new DefinitionParseException("\"" + field + "\" must be the name of a file sitting next "
                    + "to the json, not a path: no / or \\ or :, got \"" + raw + "\"");
        }
        return raw;
    }

    /// The file `name` names inside `folder`, checked lexically and by real path, or empty otherwise.
    public static Optional<Path> resolveInside(Path folder, String name) {
        Path base = folder.toAbsolutePath().normalize();
        Path file;
        try {
            file = base.resolve(name).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (!base.equals(file.getParent())) {
            return Optional.empty();
        }
        try {
            Path realBase = Files.exists(base) ? base.toRealPath() : base;
            Path realFile = Files.exists(file) ? file.toRealPath() : realBase.resolve(file.getFileName());
            return realBase.equals(realFile.getParent()) ? Optional.of(file) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /// Writes a new sibling with a random name, then moves it over the target.
    public static void writeAtomically(Path target, String content) throws IOException {
        Path folder = target.getParent();
        String name = target.getFileName().toString();
        FileAlreadyExistsException lastCollision = null;

        for (int attempt = 0; attempt < TEMP_NAME_ATTEMPTS; attempt++) {
            Path tmp = folder.resolve(name + "." + UUID.randomUUID() + ".tmp");
            try (OutputStream stream = Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (FileAlreadyExistsException e) {
                lastCollision = e;
                continue;
            } catch (IOException e) {
                deleteQuietly(tmp, e);
                throw e;
            }
            try {
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                deleteQuietly(tmp, e);
                throw e;
            }
            return;
        }
        throw lastCollision;
    }

    private static void deleteQuietly(Path tmp, IOException cause) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            cause.addSuppressed(e);
        }
    }
}
