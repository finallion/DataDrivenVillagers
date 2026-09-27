package com.lion.datadrivenvillagers.profession;

import net.minecraft.util.Identifier;

import java.util.Set;

/// Vanilla block ids that generated structures place in bulk, not world generation features. Claiming
/// one as a workstation is legal, but every placed block of that kind becomes a point of interest.
final class StructureBulkBlocks {

    private StructureBulkBlocks() {
    }

    static final Set<Identifier> IDS = Set.of(
            id("prismarine"), id("prismarine_bricks"), id("dark_prismarine"),
            id("stone_bricks"), id("nether_bricks"), id("purpur_block"),
            id("cobbled_deepslate"), id("deepslate_tiles"), id("deepslate_bricks"),
            id("mud_bricks"), id("cobblestone"),
            id("oak_planks"), id("spruce_planks"), id("birch_planks"), id("jungle_planks"),
            id("acacia_planks"), id("dark_oak_planks"), id("mangrove_planks"), id("cherry_planks"),
            id("bamboo_planks"), id("crimson_planks"), id("warped_planks"));

    private static Identifier id(String path) {
        return new Identifier("minecraft", path);
    }
}
