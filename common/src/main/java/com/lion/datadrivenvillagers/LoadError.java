package com.lion.datadrivenvillagers;

/// A rejected datapack file, reported by `/ddv errors` and `/ddv why`.
///
/// @param file   file name with extension
/// @param reason phrased for a pack author
public record LoadError(String file, String reason) {
}
