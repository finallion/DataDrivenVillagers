package com.lion.datadrivenvillagers.structure;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Draws a roofed stall, five blocks to a side, with the workstation under the roof, in the materials
/// of the village it stands in. Emitted as structure-block nbt so it passes through
/// {@code StructureTemplateManager.createTemplate} and its data fixers like an author's file.
///
/// Jigsaw orientations and levels follow `plains_small_house_1.nbt` and `streets/straight_01.nbt`;
/// a jigsaw that is off attaches to nothing and the generator does not report it.
public final class PlotGenerator {

    static final int SIZE = 5;

    /// Materials per village type, following the vanilla houses. Snowy and taiga both build in spruce,
    /// so the snowy roof is snow, as on vanilla's snowy houses.
    private record Materials(String floor, String post, String roof) {
    }

    private static final Map<String, Materials> MATERIALS = new LinkedHashMap<>();

    static {
        MATERIALS.put("plains", new Materials("minecraft:oak_planks", "minecraft:oak_log", "minecraft:oak_slab"));
        MATERIALS.put("desert", new Materials("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:smooth_sandstone_slab"));
        MATERIALS.put("savanna", new Materials("minecraft:acacia_planks", "minecraft:acacia_log", "minecraft:acacia_slab"));
        MATERIALS.put("snowy", new Materials("minecraft:spruce_planks", "minecraft:stripped_spruce_log", "minecraft:snow_block"));
        MATERIALS.put("taiga", new Materials("minecraft:spruce_planks", "minecraft:spruce_log", "minecraft:spruce_slab"));
    }

    private PlotGenerator() {
    }

    /// The village type named in a pool id; ids naming no vanilla village fall back to plains.
    public static String villageOf(Identifier poolId) {
        String path = poolId.getPath();
        for (String village : MATERIALS.keySet()) {
            if (path.contains("/" + village + "/") || path.endsWith("/" + village) || path.startsWith(village + "/")) {
                return village;
            }
        }
        return "plains";
    }

    /// A `village` outside the five vanilla types draws in plains materials.
    public static NbtCompound plot(Identifier workstation, String village) {
        return plot(workstation, village, SharedConstants.getGameVersion().dataVersion().id());
    }

    /// @param dataVersion the game's data version, passed in so tests need no {@code SharedConstants}
    static NbtCompound plot(Identifier workstation, String village, int dataVersion) {
        Materials materials = MATERIALS.getOrDefault(village, MATERIALS.get("plains"));
        Palette palette = new Palette();
        NbtList blocks = new NbtList();

        int floor = palette.of(materials.floor());
        int post = palette.of(materials.post());
        int roof = palette.of(materials.roof());
        int station = palette.of(workstation.toString());
        int torch = palette.of("minecraft:torch");

        // Joint "aligned", not "rollable": rollable would let the street rotate the plot.
        int entrance = palette.of("minecraft:jigsaw", "orientation", "west_up");
        // Villager spawn as in plains_small_house_1: a jigsaw pointing up at the villagers pool.
        int resident = palette.of("minecraft:jigsaw", "orientation", "up_north");

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x == 0 && z == 2) {
                    blocks.add(jigsaw(x, 0, z, entrance, "minecraft:building_entrance",
                            "minecraft:village/" + village + "/streets", "aligned", materials.floor()));
                } else if (x == 2 && z == 2) {
                    blocks.add(jigsaw(x, 0, z, resident, "minecraft:bottom",
                            "minecraft:village/" + village + "/villagers", "rollable", materials.floor()));
                } else {
                    blocks.add(block(x, 0, z, floor));
                }
                blocks.add(block(x, SIZE - 1, z, roof));
            }
        }
        for (int y = 1; y < SIZE - 1; y++) {
            blocks.add(block(0, y, 0, post));
            blocks.add(block(SIZE - 1, y, 0, post));
            blocks.add(block(0, y, SIZE - 1, post));
            blocks.add(block(SIZE - 1, y, SIZE - 1, post));
        }
        blocks.add(block(3, 1, 2, station));
        blocks.add(block(1, 1, 0, torch));
        blocks.add(block(1, 1, SIZE - 1, torch));

        NbtCompound root = new NbtCompound();
        root.put("size", ints(SIZE, SIZE, SIZE));
        root.put("entities", new NbtList());
        root.put("blocks", blocks);
        root.put("palette", palette.toNbt());
        root.putInt("DataVersion", dataVersion);
        return root;
    }

    private static NbtCompound block(int x, int y, int z, int state) {
        NbtCompound block = new NbtCompound();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        return block;
    }

    private static NbtCompound jigsaw(int x, int y, int z, int state, String nameAndTarget, String pool,
                                      String joint, String finalState) {
        NbtCompound block = block(x, y, z, state);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:jigsaw");
        nbt.putString("name", nameAndTarget);
        nbt.putString("target", nameAndTarget);
        nbt.putString("pool", pool);
        nbt.putString("joint", joint);
        nbt.putString("final_state", finalState);
        block.put("nbt", nbt);
        return block;
    }

    private static NbtList ints(int... values) {
        NbtList list = new NbtList();
        for (int value : values) {
            list.add(net.minecraft.nbt.NbtInt.of(value));
        }
        return list;
    }

    /// The block state list of a structure file, one entry per distinct state, referenced by index.
    private static final class Palette {
        private final List<NbtCompound> states = new ArrayList<>();

        int of(String block) {
            NbtCompound state = new NbtCompound();
            state.putString("Name", block);
            return add(state);
        }

        int of(String block, String property, String value) {
            NbtCompound state = new NbtCompound();
            state.putString("Name", block);
            NbtCompound properties = new NbtCompound();
            properties.putString(property, value);
            state.put("Properties", properties);
            return add(state);
        }

        private int add(NbtCompound state) {
            int index = states.indexOf(state);
            if (index >= 0) {
                return index;
            }
            states.add(state);
            return states.size() - 1;
        }

        NbtList toNbt() {
            NbtList list = new NbtList();
            states.forEach(list::add);
            return list;
        }
    }
}
