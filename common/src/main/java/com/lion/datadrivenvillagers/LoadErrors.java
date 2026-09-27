package com.lion.datadrivenvillagers;

import java.util.Collections;
import java.util.List;

/// One registry's rejected-file list, shared by the profession, type and structure registries.
/// Swapped whole on every write, see {@link CopyOnWrite}.
public final class LoadErrors {

    private volatile List<LoadError> errors = Collections.emptyList();

    public void add(String file, String reason) {
        errors = CopyOnWrite.plus(errors, new LoadError(file, reason));
    }

    public void clear() {
        errors = Collections.emptyList();
    }

    /// Drops the rejection of one file, so `/ddv errors` stops naming it.
    public void remove(String file) {
        errors = CopyOnWrite.minus(errors, error -> error.file().equals(file));
    }

    public List<LoadError> list() {
        return errors;
    }
}
