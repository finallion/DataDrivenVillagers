package com.lion.datadrivenvillagers.structure;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// One parsed structure file: an nbt beside the json, or a plot drawn around a workstation block, and
/// the village pools it goes into.
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

    /// A generated plot gets one template id per village type, like `bakery/desert`; an nbt file gets one only.
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
