package com.lion.datadrivenvillagers.fabric;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.DataDrivenVillagersCommand;
import com.lion.datadrivenvillagers.command.EditCommand;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.network.LookPayload;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.network.LooksBeginPayload;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

        // Payload types on both sides; client receivers are in the client entrypoint.
        PayloadTypeRegistry.playS2C().register(LooksBeginPayload.ID, LooksBeginPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LookPayload.ID, LookPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LookSync.send(handler.getPlayer()));

        // Editor. The two C2S payloads are the only client-sent packets; their contents are untrusted.
        PayloadTypeRegistry.playS2C().register(EditorOpenPayload.ID, EditorOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EditorResultPayload.ID, EditorResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorSavePayload.ID, EditorSavePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorTradesPayload.ID, EditorTradesPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EditorSavePayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> EditCommand.save(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(EditorTradesPayload.ID, (payload, context) ->
                context.player().getServer().execute(() -> EditCommand.trades(context.player(), payload)));
    }
}
