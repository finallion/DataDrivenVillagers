package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.ai.brain.MemoryQueryResult;
import net.minecraft.entity.ai.brain.task.FindPointOfInterestTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;
import java.util.stream.Stream;

/// Filters job sites a profession reserves for `villages` out of the POI search for villagers of the
/// wrong type. Filtering at the search, not at the taking: refusing in `UpdateJobSiteTask` makes the
/// villager find, forget and re-find the same block every tick. The lambda `method_46885` is the one
/// place the search knows the entity. Beds and bells pass through, no profession reserves them.
@Mixin(FindPointOfInterestTask.class)
public abstract class FindPointOfInterestTaskMixin {

    @WrapOperation(method = "method_46885",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/poi/PointOfInterestStorage;getSortedTypesAndPositions(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/world/poi/PointOfInterestStorage$OccupationStatus;)Ljava/util/stream/Stream;"))
    private static Stream<Pair<RegistryEntry<PointOfInterestType>, BlockPos>> datadrivenvillagers$skipReservedJobs(
            PointOfInterestStorage storage, Predicate<RegistryEntry<PointOfInterestType>> typePredicate,
            Predicate<BlockPos> positionPredicate, BlockPos center, int radius,
            PointOfInterestStorage.OccupationStatus status,
            Operation<Stream<Pair<RegistryEntry<PointOfInterestType>, BlockPos>>> original,
            boolean onlyRunIfChild, org.apache.commons.lang3.mutable.MutableLong nextUpdate,
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<?> positions, Predicate<?> unused,
            java.util.function.BiPredicate<?, ?> unusedToo, MemoryQueryResult<?, ?> memory, java.util.Optional<?> entityStatus,
            ServerWorld world, PathAwareEntity entity, long time) {
        Stream<Pair<RegistryEntry<PointOfInterestType>, BlockPos>> found =
                original.call(storage, typePredicate, positionPredicate, center, radius, status);
        if (!(entity instanceof VillagerEntity villager)) {
            return found;
        }
        return found.filter(pair -> ProfessionBehaviours.refusal(villager, pair.getFirst()).isEmpty());
    }
}
