package com.lion.datadrivenvillagers.platform;

import com.lion.datadrivenvillagers.network.Payload;

import dev.architectury.injectables.annotations.ExpectPlatform;

/// Client side of the channels. Separate from {@link Network} so the dedicated server never loads a
/// client-only call.
public class ClientNetwork {

    /// Receivers for the four payloads the server sends. Called from the loader's client initializer.
    @ExpectPlatform
    public static void register() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void send(Payload payload) {
        throw new AssertionError();
    }
}
