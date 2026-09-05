package com.lion.datadrivenvillagers.fabric;

import com.lion.datadrivenvillagers.client.RuntimeTextures;
import com.lion.datadrivenvillagers.client.editor.EditorFields;
import com.lion.datadrivenvillagers.client.editor.ProfessionEditorScreen;
import com.lion.datadrivenvillagers.network.ClientNetwork;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class DataDrivenVillagersFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SyncedLooks.clear();
            EditorFields.forget();
            // The images a server sent stay on the GPU otherwise, until a later join reuses the id.
            RuntimeTextures.destroyAll();
        });

        // The screen class is only referenced from client entrypoints.
        EditorBridge.bind(ProfessionEditorScreen::open, ProfessionEditorScreen::showResult);
        ClientNetwork.register();
    }
}
