package com.lion.datadrivenvillagers.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Lets the loader replace a pool's element list with one write. A worldgen thread reading the list
/// sees either the old elements or the new ones, never a partial edit.
@Mixin(StructurePool.class)
public interface StructurePoolAccessor {

    @Accessor("elements")
    ObjectArrayList<StructurePoolElement> ddv$elements();

    @Accessor("elements")
    @Mutable
    void ddv$setElements(ObjectArrayList<StructurePoolElement> elements);
}
