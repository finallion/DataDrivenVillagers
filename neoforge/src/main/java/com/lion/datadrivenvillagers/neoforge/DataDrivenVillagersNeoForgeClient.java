package com.lion.datadrivenvillagers.neoforge;

import com.lion.datadrivenvillagers.client.RuntimeTextures;
import com.lion.datadrivenvillagers.client.editor.EditorFields;
import com.lion.datadrivenvillagers.client.editor.ProfessionEditorScreen;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/// Client-only registrations. Separate class so the dedicated server never loads a signature naming
/// a client event or the editor screen.
public final class DataDrivenVillagersNeoForgeClient {

    private DataDrivenVillagersNeoForgeClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            SyncedLooks.clear();
            EditorFields.forget();
            // The images a server sent stay on the GPU otherwise, until a later join reuses the id.
            RuntimeTextures.destroyAll();
        });
        EditorBridge.bind(ProfessionEditorScreen::open, ProfessionEditorScreen::showResult);
    }
}
