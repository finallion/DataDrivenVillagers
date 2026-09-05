package com.lion.datadrivenvillagers.neoforge;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.DataDrivenVillagersCommand;
import com.lion.datadrivenvillagers.command.EditCommand;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.network.LookPayload;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.network.LooksBeginPayload;
import com.lion.datadrivenvillagers.network.SyncedLooks;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(DataDrivenVillagers.MOD_ID)
public class DataDrivenVillagersNeoForge {

    /// NeoForge fires the villager profession RegisterEvent before the point of interest one; both
    /// steps call `ProfessionLoader.prepare()`, which parses and builds once.
    public DataDrivenVillagersNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(DataDrivenVillagersNeoForge::onRegister);
        modEventBus.addListener(DataDrivenVillagersNeoForge::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(DataDrivenVillagersNeoForge::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DataDrivenVillagersNeoForge::onPlayerLoggedIn);

        // Template pools are a datapack registry, built per world: nothing to append to before a server exists.
        NeoForge.EVENT_BUS.addListener(DataDrivenVillagersNeoForge::onServerAboutToStart);

        if (FMLEnvironment.dist.isClient()) {
            DataDrivenVillagersNeoForgeClient.init();
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

    /// This class is loaded on the dedicated server, so handlers reference only `SyncedLooks` and
    /// `EditorBridge`, never a screen class. Handlers run on the main thread, on the client the render
    /// thread.
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(LooksBeginPayload.ID, LooksBeginPayload.CODEC,
                (payload, context) -> SyncedLooks.begin(payload));
        registrar.playToClient(LookPayload.ID, LookPayload.CODEC,
                (payload, context) -> SyncedLooks.accept(payload));

        registrar.playToClient(EditorOpenPayload.ID, EditorOpenPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> EditorBridge.open(payload)));
        registrar.playToClient(EditorResultPayload.ID, EditorResultPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> EditorBridge.result(payload)));
        registrar.playToServer(EditorSavePayload.ID, EditorSavePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayerEntity player) {
                        EditCommand.save(player, payload);
                    }
                }));
        registrar.playToServer(EditorTradesPayload.ID, EditorTradesPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayerEntity player) {
                        EditCommand.trades(player, payload);
                    }
                }));
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
