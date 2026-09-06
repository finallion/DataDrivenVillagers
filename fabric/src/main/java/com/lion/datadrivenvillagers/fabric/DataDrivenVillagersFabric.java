package com.lion.datadrivenvillagers.fabric;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.DataDrivenVillagersCommand;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.platform.Network;
import com.lion.datadrivenvillagers.structure.StructureLoader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class DataDrivenVillagersFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DataDrivenVillagers.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> DataDrivenVillagersCommand.register(dispatcher, access));

        // Template pools are a datapack registry, built per world: nothing to append to before a server exists.
        ServerLifecycleEvents.SERVER_STARTING.register(StructureLoader::load);

        Network.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LookSync.send(handler.getPlayer()));
    }
}
