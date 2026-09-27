package com.lion.datadrivenvillagers.mixin;

import com.mojang.datafixers.util.Either;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Reads which template id or inline template a pool element draws from.
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {

    @Accessor("location")
    Either<Identifier, StructureTemplate> ddv$location();
}
