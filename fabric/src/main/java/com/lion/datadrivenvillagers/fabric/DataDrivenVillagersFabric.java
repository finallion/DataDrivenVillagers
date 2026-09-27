package com.lion.datadrivenvillagers.fabric;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.DataDrivenVillagersCommand;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.platform.Network;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKeys;

public class DataDrivenVillagersFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DataDrivenVillagers.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> DataDrivenVillagersCommand.register(dispatcher, access));

        // Client tag loads fire in single player too; the claim maps are server-side state.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (!client) {
                TypeLoader.claimTags(registries.get(RegistryKeys.BIOME));
            }
        });

        // A tag claim from a past world must not survive into the next one in the same session.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TypeLoader.releaseTagClaims());
        // Template pools are a datapack registry, built per world: nothing to append to before a server exists.
        ServerLifecycleEvents.SERVER_STARTING.register(StructureLoader::load);
        // Restores POI claims a registry sync dropped; retries overrides whose target registered late.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ProfessionLoader.reapplyAtServerStart());

        Network.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LookSync.send(handler.getPlayer()));
    }
}
