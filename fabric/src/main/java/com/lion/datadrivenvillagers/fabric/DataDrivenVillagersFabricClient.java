package com.lion.datadrivenvillagers.fabric;

import com.lion.datadrivenvillagers.client.RuntimeTextures;
import com.lion.datadrivenvillagers.client.editor.EditorFields;
import com.lion.datadrivenvillagers.client.editor.ProfessionEditorScreen;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.LookPayload;
import com.lion.datadrivenvillagers.network.LooksBeginPayload;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/// Client receivers. They run on the client thread, which is the render thread, so no lock around
/// {@link SyncedLooks}.
public class DataDrivenVillagersFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LooksBeginPayload.ID, (payload, context) -> SyncedLooks.begin(payload));
        ClientPlayNetworking.registerGlobalReceiver(LookPayload.ID, (payload, context) -> SyncedLooks.accept(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SyncedLooks.clear();
            EditorFields.forget();
            // The images a server sent stay on the GPU otherwise, until a later join reuses the id.
            RuntimeTextures.destroyAll();
        });

        // The screen class is only referenced from client entrypoints.
        EditorBridge.bind(ProfessionEditorScreen::open, ProfessionEditorScreen::showResult);
        ClientPlayNetworking.registerGlobalReceiver(EditorOpenPayload.ID, (payload, context) ->
                context.client().execute(() -> EditorBridge.open(payload)));
        ClientPlayNetworking.registerGlobalReceiver(EditorResultPayload.ID, (payload, context) ->
                context.client().execute(() -> EditorBridge.result(payload)));
    }
}
