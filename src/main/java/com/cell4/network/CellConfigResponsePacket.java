package com.cell4.network;

import com.cell4.client.CellConfiguratorScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C packet sent from server to client after a configuration save attempt.
 * Provides success/failure feedback to the configurator screen.
 */
public class CellConfigResponsePacket {
    private final boolean success;
    private final String message;

    public CellConfigResponsePacket(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static void encode(CellConfigResponsePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.success);
        buf.writeUtf(pkt.message, 4096);
    }

    public static CellConfigResponsePacket decode(FriendlyByteBuf buf) {
        return new CellConfigResponsePacket(buf.readBoolean(), buf.readUtf(4096));
    }

    public static void handle(CellConfigResponsePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side: store the response for the screen to pick up
            CellConfiguratorScreen.setLastSaveResult(pkt.success, pkt.message);
        });
        ctx.get().setPacketHandled(true);
    }
}
