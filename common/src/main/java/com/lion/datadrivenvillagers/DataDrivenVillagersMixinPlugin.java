package com.lion.datadrivenvillagers;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/// NeoForge replaces vanilla's hero gift map with a data map, so the vanilla gift mixin has no target
/// there and the neoforge module brings its own. Every other mixin applies on every loader.
public class DataDrivenVillagersMixinPlugin implements IMixinConfigPlugin {

    private static final String VANILLA_GIFTS = "com.lion.datadrivenvillagers.mixin.GiveGiftsToHeroTaskMixin";
    private static final String NEOFORGE_LOADER = "net.neoforged.fml.loading.FMLLoader";

    private boolean neoForge;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName(NEOFORGE_LOADER, false, getClass().getClassLoader());
            neoForge = true;
        } catch (ClassNotFoundException absent) {
            neoForge = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(neoForge && VANILLA_GIFTS.equals(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
