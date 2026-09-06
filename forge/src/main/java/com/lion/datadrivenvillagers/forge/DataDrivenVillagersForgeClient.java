package com.lion.datadrivenvillagers.forge;

import com.lion.datadrivenvillagers.client.RuntimeTextures;
import com.lion.datadrivenvillagers.client.editor.EditorFields;
import com.lion.datadrivenvillagers.client.editor.ProfessionEditorScreen;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.SyncedLooks;
import com.lion.datadrivenvillagers.platform.ClientNetwork;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/// Client-only registrations. Separate class so the dedicated server never loads a signature naming
/// a client event or the editor screen.
public final class DataDrivenVillagersForgeClient {

    private DataDrivenVillagersForgeClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            SyncedLooks.clear();
            EditorFields.forget();
            // The images a server sent stay on the GPU otherwise, until a later join reuses the id.
            RuntimeTextures.destroyAll();
        });
        EditorBridge.bind(ProfessionEditorScreen::open, ProfessionEditorScreen::showResult);
        ClientNetwork.register();
    }
}
