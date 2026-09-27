package com.lion.datadrivenvillagers.profession;

import net.minecraft.util.Identifier;

import java.util.Set;

/// Vanilla block ids that world generation writes into chunk sections without registering a point of
/// interest. Claiming one as a workstation turns every natural block of that kind into a job site.
final class NaturalBlocks {

    private NaturalBlocks() {
    }

    static final Set<Identifier> IDS = Set.of(
            id("stone"), id("granite"), id("diorite"), id("andesite"), id("tuff"), id("deepslate"),
            id("calcite"), id("smooth_basalt"), id("basalt"), id("blackstone"), id("netherrack"),
            id("end_stone"), id("bedrock"),
            id("dirt"), id("grass_block"), id("podzol"), id("mycelium"), id("coarse_dirt"),
            id("rooted_dirt"), id("mud"), id("clay"), id("gravel"),
            id("sand"), id("red_sand"), id("sandstone"), id("red_sandstone"),
            id("terracotta"), id("white_terracotta"), id("orange_terracotta"), id("yellow_terracotta"),
            id("brown_terracotta"), id("red_terracotta"), id("light_gray_terracotta"),
            id("snow_block"), id("snow"), id("powder_snow"), id("ice"), id("packed_ice"), id("blue_ice"),
            id("moss_block"), id("sculk"), id("dripstone_block"), id("amethyst_block"), id("budding_amethyst"),
            id("amethyst_cluster"), id("pointed_dripstone"), id("moss_carpet"), id("glowstone"), id("shroomlight"),
            id("cactus"), id("bamboo"), id("chorus_plant"),
            id("magma_block"), id("soul_sand"), id("soul_soil"),
            id("oak_log"), id("spruce_log"), id("birch_log"), id("jungle_log"), id("acacia_log"),
            id("dark_oak_log"), id("mangrove_log"), id("cherry_log"), id("crimson_stem"), id("warped_stem"),
            id("oak_leaves"), id("spruce_leaves"), id("birch_leaves"), id("jungle_leaves"),
            id("acacia_leaves"), id("dark_oak_leaves"), id("mangrove_leaves"), id("cherry_leaves"),
            id("azalea_leaves"), id("flowering_azalea_leaves"),
            id("nether_wart_block"), id("warped_wart_block"), id("crimson_nylium"), id("warped_nylium"),
            id("mangrove_roots"), id("muddy_mangrove_roots"),
            id("coal_ore"), id("deepslate_coal_ore"), id("iron_ore"), id("deepslate_iron_ore"),
            id("copper_ore"), id("deepslate_copper_ore"), id("gold_ore"), id("deepslate_gold_ore"),
            id("redstone_ore"), id("deepslate_redstone_ore"), id("lapis_ore"), id("deepslate_lapis_ore"),
            id("diamond_ore"), id("deepslate_diamond_ore"), id("emerald_ore"), id("deepslate_emerald_ore"),
            id("nether_quartz_ore"), id("nether_gold_ore"), id("ancient_debris"),
            id("raw_iron_block"), id("raw_copper_block"),
            id("infested_stone"), id("infested_deepslate"),
            id("brown_mushroom_block"), id("red_mushroom_block"), id("mushroom_stem"),
            id("tube_coral_block"), id("brain_coral_block"), id("bubble_coral_block"), id("fire_coral_block"),
            id("horn_coral_block"));

    private static Identifier id(String path) {
        return new Identifier("minecraft", path);
    }
}
