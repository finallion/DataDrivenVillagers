package com.lion.datadrivenvillagers.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The plot is checked against the shape a structure block saves and against the two jigsaw blocks a
/// village needs, because those are the parts that fail silently when wrong: a village piece that
/// does not attach is simply never placed.
class PlotGeneratorTest {

    private static final Identifier CAMPFIRE = Identifier.of("minecraft", "campfire");

    private static NbtList compounds(NbtCompound owner, String key) {
        return owner.getList(key, NbtElement.COMPOUND_TYPE);
    }

    private static NbtList ints(NbtCompound owner, String key) {
        return owner.getList(key, NbtElement.INT_TYPE);
    }

    /// -1 for missing: nbt reads an absent key or index as 0, and the assertions expect real zeros.
    private static int intOf(NbtCompound owner, String key) {
        return owner.contains(key, NbtElement.INT_TYPE) ? owner.getInt(key) : -1;
    }

    private static int at(NbtList list, int index) {
        return index < list.size() ? list.getInt(index) : -1;
    }

    private static List<NbtCompound> blocks(NbtCompound plot) {
        List<NbtCompound> result = new ArrayList<>();
        NbtList list = compounds(plot, "blocks");
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

    private static String nameOf(NbtCompound plot, NbtCompound block) {
        int state = intOf(block, "state");
        return compounds(plot, "palette").getCompound(state).getString("Name");
    }

    private static List<NbtCompound> jigsaws(NbtCompound plot) {
        return blocks(plot).stream().filter(block -> nameOf(plot, block).equals("minecraft:jigsaw")).toList();
    }

    @Test
    void hasTheShapeOfAStructureFile() {
        NbtCompound plot = PlotGenerator.plot(CAMPFIRE, "plains", 4440);

        assertEquals(4440, intOf(plot, "DataVersion"));
        assertEquals(3, ints(plot, "size").size());
        assertEquals(5, at(ints(plot, "size"), 0));
        assertTrue(plot.contains("entities"));
        int palette = compounds(plot, "palette").size();
        for (NbtCompound block : blocks(plot)) {
            int state = intOf(block, "state");
            assertTrue(state >= 0 && state < palette, "state index " + state + " points into the palette");
            assertEquals(3, ints(block, "pos").size());
        }
    }

    @Test
    void standsOnTheStreetAndBringsItsOwnVillager() {
        NbtCompound plot = PlotGenerator.plot(CAMPFIRE, "desert", 4440);
        List<NbtCompound> jigsaws = jigsaws(plot);
        assertEquals(2, jigsaws.size());

        NbtCompound entrance = jigsaws.stream()
                .filter(j -> j.getCompound("nbt").getString("name").equals("minecraft:building_entrance"))
                .findFirst().orElseThrow();
        NbtCompound entranceNbt = entrance.getCompound("nbt");
        assertEquals("minecraft:village/desert/streets", entranceNbt.getString("pool"));
        assertEquals("minecraft:building_entrance", entranceNbt.getString("target"));
        assertEquals("aligned", entranceNbt.getString("joint"));
        assertEquals(0, at(ints(entrance, "pos"), 0), "on the west edge");
        assertEquals(0, at(ints(entrance, "pos"), 1), "at street level");
        assertEquals("west_up", compounds(plot, "palette")
                .getCompound(intOf(entrance, "state"))
                .getCompound("Properties").getString("orientation"));

        NbtCompound resident = jigsaws.stream()
                .filter(j -> j.getCompound("nbt").getString("pool").endsWith("/villagers"))
                .findFirst().orElseThrow();
        assertEquals("minecraft:village/desert/villagers", resident.getCompound("nbt").getString("pool"));
        assertEquals("minecraft:bottom", resident.getCompound("nbt").getString("name"));
        assertEquals(0, at(ints(resident, "pos"), 1), "in the floor");
    }

    @Test
    void putsTheWorkstationUnderTheRoof() {
        NbtCompound plot = PlotGenerator.plot(CAMPFIRE, "taiga", 4440);
        List<NbtCompound> stations = blocks(plot).stream()
                .filter(block -> nameOf(plot, block).equals("minecraft:campfire")).toList();
        assertEquals(1, stations.size());
        assertEquals(1, at(ints(stations.get(0), "pos"), 1), "standing on the floor");
    }

    @Test
    void buildsInTheMaterialsOfTheVillage() {
        assertTrue(plot("desert").contains("minecraft:sandstone"));
        assertTrue(plot("savanna").contains("minecraft:acacia_planks"));
        assertTrue(plot("snowy").contains("minecraft:snow_block"));
        assertTrue(plot("plains").contains("minecraft:oak_log"));
        assertTrue(plot("somebody_elses_pool").contains("minecraft:oak_log"), "unknown villages build like plains");
    }

    private static List<String> plot(String village) {
        NbtCompound plot = PlotGenerator.plot(CAMPFIRE, village, 4440);
        List<String> names = new ArrayList<>();
        NbtList palette = compounds(plot, "palette");
        for (int i = 0; i < palette.size(); i++) {
            names.add(palette.getCompound(i).getString("Name"));
        }
        return names;
    }

    @Test
    void readsTheVillageOffThePoolId() {
        assertEquals("desert", PlotGenerator.villageOf(Identifier.of("minecraft", "village/desert/houses")));
        assertEquals("snowy", PlotGenerator.villageOf(Identifier.of("minecraft", "village/snowy/decor")));
        assertEquals("plains", PlotGenerator.villageOf(Identifier.of("othermod", "market/stalls")));
    }
}
