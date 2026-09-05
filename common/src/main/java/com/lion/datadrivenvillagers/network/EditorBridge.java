package com.lion.datadrivenvillagers.network;

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

    public static void open(EditorOpenPayload payload) {
        if (opener != null) {
            opener.accept(payload);
        }
    }

    public static void result(EditorResultPayload payload) {
        if (reporter != null) {
            reporter.accept(payload);
        }
    }
}
