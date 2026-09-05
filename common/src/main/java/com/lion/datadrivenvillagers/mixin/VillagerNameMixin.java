package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// `display_name`. `getDefaultName` builds `entity.minecraft.villager.<path>` from the profession's
/// registry id and translates it without a fallback, so an untranslated key would render raw.
@Mixin(VillagerEntity.class)
public abstract class VillagerNameMixin {

    @ModifyReturnValue(method = "getDefaultName", at = @At("RETURN"))
    private Text datadrivenvillagers$displayName(Text original) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        Identifier profession =
                Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession());
        if (profession == null) {
            return original;
        }
        return ProfessionLoader.displayName(profession).orElse(original);
    }
}
