package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.command.EditCommand;
import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.network.LookPayload;
import com.lion.datadrivenvillagers.network.LooksBeginPayload;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Forge has no registry of channel ids: one channel carries all six packets and the index is the
/// packet id. Both sides register the same list in the same order — the server included, because the
/// encoder of a server-to-client packet lives there.
///
/// The client-only calls sit in a nested lambda on purpose. That lambda is created when the handler
/// runs, which happens on a client and nowhere else, so a dedicated server never loads
/// {@link SyncedLooks} and through it the render classes.
final class Channel {

    private static final String PROTOCOL = "1";

    static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
            .named(DataDrivenVillagers.id("main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private Channel() {
    }

    static void register() {
        INSTANCE.registerMessage(0, LooksBeginPayload.class,
                (payload, buf) -> payload.write(buf), LooksBeginPayload::read,
                (payload, context) -> onClient(context, () -> SyncedLooks.begin(payload)));
        INSTANCE.registerMessage(1, LookPayload.class,
                (payload, buf) -> payload.write(buf), LookPayload::read,
                (payload, context) -> onClient(context, () -> SyncedLooks.accept(payload)));
        INSTANCE.registerMessage(2, EditorOpenPayload.class,
                (payload, buf) -> payload.write(buf), EditorOpenPayload::read,
                (payload, context) -> onClient(context, () -> EditorBridge.open(payload)));
        INSTANCE.registerMessage(3, EditorResultPayload.class,
                (payload, buf) -> payload.write(buf), EditorResultPayload::read,
                (payload, context) -> onClient(context, () -> EditorBridge.result(payload)));

        // The only two a client sends, and their contents are untrusted.
        INSTANCE.registerMessage(4, EditorSavePayload.class,
                (payload, buf) -> payload.write(buf), EditorSavePayload::read,
                (payload, context) -> onServer(context, player -> EditCommand.save(player, payload)));
        INSTANCE.registerMessage(5, EditorTradesPayload.class,
                (payload, buf) -> payload.write(buf), EditorTradesPayload::read,
                (payload, context) -> onServer(context, player -> EditCommand.trades(player, payload)));
    }

    /// Decoding already happened on the netty thread; this queues the work onto the client thread.
    private static void onClient(Supplier<NetworkEvent.Context> context, Runnable work) {
        context.get().enqueueWork(work);
        context.get().setPacketHandled(true);
    }

    private static void onServer(Supplier<NetworkEvent.Context> context, Consumer<ServerPlayerEntity> work) {
        ServerPlayerEntity player = context.get().getSender();
        if (player != null) {
            context.get().enqueueWork(() -> work.accept(player));
        }
        context.get().setPacketHandled(true);
    }
}
