package com.lion.datadrivenvillagers;

/// What a reload did to one file. {@link Kind#RESTART} names every field that lives in a frozen
/// registry and could not be applied.
///
/// @param file   file name with extension
/// @param detail phrased for a pack author, empty when the kind says everything
public record ReloadOutcome(String file, Kind kind, String detail) {

    public enum Kind {
        /// Read again and applied in full.
        UPDATED,
        /// Read again, nothing in it had changed.
        UNCHANGED,
        /// Read again, but part of it only takes effect after a restart.
        RESTART,
        /// Could not be read. Reason in {@link #detail}.
        REJECTED
    }

    public static ReloadOutcome updated(String file, String detail) {
        return new ReloadOutcome(file, Kind.UPDATED, detail);
    }

    public static ReloadOutcome unchanged(String file) {
        return new ReloadOutcome(file, Kind.UNCHANGED, "");
    }

    public static ReloadOutcome restart(String file, String detail) {
        return new ReloadOutcome(file, Kind.RESTART, detail);
    }

    public static ReloadOutcome rejected(String file, String detail) {
        return new ReloadOutcome(file, Kind.REJECTED, detail);
    }
}
