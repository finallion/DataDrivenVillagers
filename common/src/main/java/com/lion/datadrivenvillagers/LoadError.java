package com.lion.datadrivenvillagers;

/// @param file   file name with extension
/// @param reason phrased for a pack author
public record LoadError(String file, String reason) {
}
