package com.lion.datadrivenvillagers.network;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.JsonDepth;

import java.util.function.Consumer;

/// Lets the play-to-client payload handlers reach the editor screen without naming a client class,
/// since both loaders register those handlers in code that also runs on a dedicated server. Unbound
/// there, so a received packet does nothing.
public final class EditorBridge {

    private static Consumer<EditorOpenPayload> opener;
    private static Consumer<EditorResultPayload> reporter;

    private EditorBridge() {
    }

    public static void bind(Consumer<EditorOpenPayload> open, Consumer<EditorResultPayload> result) {
        opener = open;
        reporter = result;
    }

    /// Refuses a json too deep for the screen to later write back out; the server may hold one from any source.
    public static void open(EditorOpenPayload payload) {
        if (opener == null) {
            return;
        }
        if (tooDeep(payload)) {
            DataDrivenVillagers.LOGGER.warn("{}: json from the server nests too deep, the editor was not opened",
                    payload.fileName());
            return;
        }
        opener.accept(payload);
    }

    private static boolean tooDeep(EditorOpenPayload payload) {
        try {
            return JsonDepth.exceeds(JsonParser.parseString(payload.json()), JsonDepth.MAX_DEPTH);
        } catch (JsonSyntaxException | IllegalStateException e) {
            // Not readable as json at all; the screen shows that itself, in its own words.
            return false;
        }
    }

    public static void result(EditorResultPayload payload) {
        if (reporter != null) {
            reporter.accept(payload);
        }
    }
}
