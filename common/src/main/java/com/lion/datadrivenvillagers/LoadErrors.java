package com.lion.datadrivenvillagers;

import java.util.Collections;
import java.util.List;

/// Swapped whole on every write, so readers on other threads always see a complete list.
public final class LoadErrors {

    private volatile List<LoadError> errors = Collections.emptyList();

    public void add(String file, String reason) {
        errors = CopyOnWrite.plus(errors, new LoadError(file, reason));
    }

    public void clear() {
        errors = Collections.emptyList();
    }

    public void remove(String file) {
        errors = CopyOnWrite.minus(errors, error -> error.file().equals(file));
    }

    public List<LoadError> list() {
        return errors;
    }
}
