package com.lion.datadrivenvillagers.structure;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// One parsed structure file: an nbt beside the json, or a plot drawn around a workstation block, and
/// the village pools it goes into.
///
/// @param id          registry id, derived from the file name
/// @param file        the nbt beside the json, empty for a generated plot
/// @param workstation the block a generated plot is built around, empty for an author's nbt
/// @param weight      draws against the other pieces of the pool; vanilla houses sit between 1 and 3
/// @param villages    village types, empty meaning all of them
/// @param pool        houses, decor or streets
/// @param pools       pool ids written out; replaces {@link #villages} and {@link #pool} when present
/// @param ground      how the piece meets the terrain
/// @param processors  optional processor list, e.g. `minecraft:mossify_10_percent`
public record StructureDefinition(
        Identifier id,
        Optional<String> file,
        Optional<Identifier> workstation,
        int weight,
        List<String> villages,
        String pool,
        List<Identifier> pools,
        GroundKind ground,
        Optional<Identifier> processors
) {

    public String name() {
        return id.getPath();
    }

    public boolean generated() {
        return workstation.isPresent();
    }

    /// Every pool this piece is added to, from `pools` or from `villages` x `pool`.
    public List<Identifier> targetPools() {
        if (!pools.isEmpty()) {
            return pools;
        }
        return villages.stream()
                .map(village -> Identifier.of("minecraft", "village/" + village + "/" + pool))
                .toList();
    }

    /// The template id the pool element points at: the definition id for an nbt, one id per village
    /// type for a generated plot (`bakery/desert`), since the plot is drawn in that village's materials.
    public Identifier templateId(Identifier poolId) {
        if (!generated()) {
            return id;
        }
        return Identifier.of(id.getNamespace(), id.getPath() + "/" + PlotGenerator.villageOf(poolId));
    }

    /// The template `/ddv why` inspects: the first pool's.
    public Identifier firstTemplateId() {
        List<Identifier> pools = targetPools();
        return pools.isEmpty() ? templateId(Identifier.of("minecraft", "village/plains/houses")) : templateId(pools.get(0));
    }
}
