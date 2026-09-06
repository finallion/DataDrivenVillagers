package com.lion.datadrivenvillagers.forge;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.DataDrivenVillagersCommand;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.platform.Network;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

@Mod(DataDrivenVillagers.MOD_ID)
public class DataDrivenVillagersForge {

    /// Forge fires the villager profession RegisterEvent before the point of interest one; both steps
    /// call `ProfessionLoader.prepare()`, which parses and builds once.
    public DataDrivenVillagersForge() {
        DataDrivenVillagers.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(DataDrivenVillagersForge::onRegister);
        MinecraftForge.EVENT_BUS.addListener(DataDrivenVillagersForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(DataDrivenVillagersForge::onPlayerLoggedIn);

        // Template pools are a datapack registry, built per world: nothing to append to before a server exists.
        MinecraftForge.EVENT_BUS.addListener(DataDrivenVillagersForge::onServerAboutToStart);

        Network.register();

        if (FMLEnvironment.dist.isClient()) {
            DataDrivenVillagersForgeClient.init();
        }
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        StructureLoader.load(event.getServer());
    }

    private static void onRegister(RegisterEvent event) {
        if (RegistryKeys.POINT_OF_INTEREST_TYPE.equals(event.getRegistryKey())) {
            ProfessionLoader.registerPointsOfInterest();
        } else if (RegistryKeys.VILLAGER_PROFESSION.equals(event.getRegistryKey())) {
            ProfessionLoader.registerProfessions();
        } else if (RegistryKeys.VILLAGER_TYPE.equals(event.getRegistryKey())) {
            TypeLoader.registerTypes();
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity player) {
            LookSync.send(player);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        DataDrivenVillagersCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
